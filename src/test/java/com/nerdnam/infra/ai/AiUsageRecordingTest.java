package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * AI 호출의 토큰 사용량 기록 (BE-20, 이슈 #39).
 *
 * <p>기록 한 줄은 <b>재시도를 포함한 호출 1건</b>이다. 실패한 시도도 토큰을 쓰므로
 * 마지막 응답의 {@code usage} 만 저장하면 재시도로 쓴 토큰이 빠진다 — 그 합산을 고정한다.
 */
class AiUsageRecordingTest {

    private static final String BASE = "http://ai.test";

    private MockRestServiceServer server;
    private AiServerClient client;
    private final List<Recorded> records = new ArrayList<>();

    record Recorded(String operation, boolean success, AiUsage usage) {
    }

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        AiCallLog log = (context, operation, aiModel, promptVersion, success, errorCode, latencyMs, usage) ->
                records.add(new Recorded(operation, success, usage));
        client = new RecordingAiServerClient(new HttpAiServerClient(builder.build()), log);
    }

    @Test
    void 재시도로_쓴_토큰까지_더해_한_줄로_기록한다() {
        // 첫 시도는 503 인데 그때까지 쓴 토큰이 오류 본문에 담겨 온다 (계약 v0.4.0 ErrorResponse)
        server.expect(once(), requestTo(BASE + "/restructure"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"detail": "bedrock unavailable ThrottlingException",
                                 "usage": {"input_tokens": 100, "output_tokens": 10, "llm_calls": 1}}
                                """));
        server.expect(once(), requestTo(BASE + "/restructure"))
                .andRespond(withSuccess("""
                        {"model": "nova", "prompt_version": "p1_v4",
                         "usage": {"input_tokens": 3000, "output_tokens": 120, "llm_calls": 2},
                         "episodes": [{"title": "t", "problem": "p", "solution": "s", "result": "r"}]}
                        """, MediaType.APPLICATION_JSON));

        RestructureResult result = client.restructure(new RestructureRequest(RestructureRequest.MODE_INITIAL,
                "제목", "원문", List.of(), List.of(), List.of(), null, null, null));

        assertThat(result.usage()).isEqualTo(new AiUsage(3000, 120, 2));   // 응답은 마지막 시도의 값
        assertThat(records).hasSize(1);
        assertThat(records.getFirst().success()).isTrue();
        // 기록은 실패한 시도(100/10/1)까지 더한 합이다
        assertThat(records.getFirst().usage()).isEqualTo(new AiUsage(3100, 130, 3));
        server.verify();
    }

    @Test
    void 끝까지_실패하면_실패한_시도들이_쓴_토큰을_기록한다() {
        server.expect(once(), requestTo(BASE + "/match"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"x\", \"usage\": {\"input_tokens\": 40, \"output_tokens\": 5, \"llm_calls\": 1}}"));
        server.expect(once(), requestTo(BASE + "/match"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"x\", \"usage\": {\"input_tokens\": 60, \"output_tokens\": 7, \"llm_calls\": 1}}"));

        assertThatThrownBy(() -> client.match(new MatchCall.Request(List.of())))
                .isInstanceOf(AiServerUnavailableException.class);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().success()).isFalse();
        assertThat(records.getFirst().usage()).isEqualTo(new AiUsage(100, 12, 2));
        server.verify();
    }

    /** 토큰을 알 수 없으면 0 이 아니라 null 로 남긴다 — "안 썼다"와 "모른다"를 구분한다 */
    @Test
    void 오류_본문에_usage가_없으면_null로_남긴다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"detail\": \"bedrock timeout\"}"));

        assertThatThrownBy(() -> client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.QUERY))
                .isInstanceOf(AiServerUnavailableException.class);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().usage()).isNull();
        server.verify();
    }

    /** 성공한 /embed 는 LLM 을 부르지 않아 계약에 usage 가 없다 → null */
    @Test
    void 임베딩_성공은_usage가_없으므로_null이다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withSuccess("{\"vectors\": [[0.1]], \"dim\": 1, \"embedding_model\": \"titan\"}",
                        MediaType.APPLICATION_JSON));

        client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.QUERY);

        assertThat(records).hasSize(1);
        assertThat(records.getFirst().operation()).isEqualTo("EMBED");
        assertThat(records.getFirst().usage()).isNull();
        server.verify();
    }
}
