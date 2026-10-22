package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 가짜 HTTP 서버(MockRestServiceServer)로 AI 서버 응답을 흉내 내 클라이언트 동작만 확인한다 */
class HttpAiServerClientTest {

    private static final String BASE = "http://ai.test";

    private MockRestServiceServer server;
    private HttpAiServerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpAiServerClient(builder.build());
    }

    @Test
    void 임베딩_응답을_요청_순서대로_벡터로_바꾼다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.purpose").value("document"))
                .andExpect(jsonPath("$.texts[1]").value("두 번째"))
                .andRespond(withSuccess("""
                        {"vectors": [[0.1, 0.2], [0.3, 0.4]], "embedding_model": "google/embeddinggemma-2@rev:768"}
                        """, MediaType.APPLICATION_JSON));

        Embeddings result = client.embed(List.of("첫 번째", "두 번째"), EmbedPurpose.DOCUMENT);

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(1)).containsExactly(0.3f, 0.4f);
        assertThat(result.embeddingModel()).isEqualTo("google/embeddinggemma-2@rev:768");
        server.verify();
    }

    @Test
    void 모델이_준비되지_않은_503은_한_번_더_시도하고_GPU_꺼짐으로_본다() {
        server.expect(times(2), requestTo(BASE + "/embed"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.embed(List.of("a"), EmbedPurpose.QUERY))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.GPU_UNAVAILABLE));
        server.verify();
    }

    @Test
    void 요청이_잘못된_4xx는_다시_시도하지_않는다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.embed(List.of("a"), EmbedPurpose.QUERY))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR));
        server.verify();
    }

    @Test
    void 서버에_연결되지_않으면_GPU_꺼짐으로_본다() {
        HttpAiServerClient unreachable = HttpAiServerClient.create(
                new AiServerProperties("http://127.0.0.1:1", false, Duration.ofMillis(500), Duration.ofSeconds(1)));

        assertThatThrownBy(() -> unreachable.embed(List.of("a"), EmbedPurpose.DOCUMENT))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.GPU_UNAVAILABLE));
        assertThat(unreachable.health().ready()).isFalse();
    }

    @Test
    void 모든_모델이_ready_일_때만_준비_상태다() {
        server.expect(once(), requestTo(BASE + "/health"))
                .andRespond(withSuccess("""
                        {"status": "ok", "models": {"llm": "ready", "embedding": "loading"}}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE + "/health"))
                .andRespond(withSuccess("""
                        {"status": "ok", "models": {"llm": "ready", "embedding": "ready"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.health().ready()).isFalse();
        assertThat(client.health().ready()).isTrue();
        server.verify();
    }
}
