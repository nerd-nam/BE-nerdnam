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

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
    void 요건_추출_v030_응답의_약어_확장어와_연차를_읽는다() {
        server.expect(once(), requestTo(BASE + "/requirements/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.lines[0].text").value("MSA 개발 3년 이상"))
                .andExpect(jsonPath("$.lines[0].type").value("required"))
                .andRespond(withSuccess("""
                        {"lines": [
                          {"id": 2, "skills_raw": ["Redis"], "expansions": [], "min_years": null},
                          {"id": 1, "skills_raw": ["MSA"],
                           "expansions": ["Microservice Architecture", "마이크로서비스 아키텍처"], "min_years": 3}
                        ], "model": "Qwen/Qwen3.5-4B", "prompt_version": "p2_v2"}
                        """, MediaType.APPLICATION_JSON));

        RequirementExtraction.Result result = client.extractRequirements(new RequirementExtraction.Request(List.of(
                new RequirementExtraction.Line(1L, "required", "MSA 개발 3년 이상"),
                new RequirementExtraction.Line(2L, "preferred", "Redis 경험"))));

        assertThat(result.lines().getFirst().expansions()).isEmpty();
        assertThat(result.lines().get(1)).isEqualTo(new RequirementExtraction.Extracted(1L, List.of("MSA"),
                List.of("Microservice Architecture", "마이크로서비스 아키텍처"), 3));
        assertThat(result.promptVersion()).isEqualTo("p2_v2");
        server.verify();
    }

    @Test
    void 임베딩_응답을_요청_순서대로_벡터로_바꾼다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.purpose").value("document"))
                .andExpect(jsonPath("$.items[0].title").value("Redis"))
                .andExpect(jsonPath("$.items[1].text").value("두 번째"))
                .andExpect(jsonPath("$.items[1].title").doesNotExist())   // 제목은 선택 — 없으면 보내지 않는다
                .andRespond(withSuccess("""
                        {"vectors": [[0.1, 0.2], [0.3, 0.4]], "dim": 2,
                         "embedding_model": "google/embeddinggemma-2@rev:768"}
                        """, MediaType.APPLICATION_JSON));

        Embeddings result = client.embed(
                List.of(new EmbedItem("첫 번째", "Redis"), EmbedItem.of("두 번째")), EmbedPurpose.DOCUMENT);

        assertThat(result.vectors()).hasSize(2);
        assertThat(result.vectors().get(1)).containsExactly(0.3f, 0.4f);
        assertThat(result.embeddingModel()).isEqualTo("google/embeddinggemma-2@rev:768");
        assertThat(result.stub()).isFalse();
        server.verify();
    }

    @Test
    void 벡터_차원이_응답의_dim_과_다르면_AI_오류() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withSuccess("""
                        {"vectors": [[0.1, 0.2]], "dim": 768, "embedding_model": "m:768"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.DOCUMENT))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR));
        server.verify();
    }

    @Test
    void 가짜_응답_모드는_embedding_model_로_알아낸다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withSuccess("""
                        {"vectors": [[0.1, 0.2]], "embedding_model": "stub"}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.DOCUMENT).stub()).isTrue();
        server.verify();
    }

    @Test
    void 모델이_준비되지_않은_503은_한_번_더_시도하고_GPU_꺼짐으로_본다() {
        server.expect(times(2), requestTo(BASE + "/embed"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.QUERY))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.GPU_UNAVAILABLE));
        server.verify();
    }

    @Test
    void 요청이_잘못된_4xx는_다시_시도하지_않는다() {
        server.expect(once(), requestTo(BASE + "/embed"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.QUERY))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR));
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"INTERNAL_SERVER_ERROR", "BAD_GATEWAY"})
    void 그_밖의_5xx는_한_번_더_시도하고_AI_오류로_본다(HttpStatus status) {
        server.expect(times(2), requestTo(BASE + "/embed"))
                .andRespond(withStatus(status));

        assertThatThrownBy(() -> client.embed(List.of(EmbedItem.of("a")), EmbedPurpose.QUERY))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR));
        server.verify();
    }

    @Test
    void AI_서버의_504는_다시_시도하지_않고_TIMEOUT() {
        server.expect(once(), requestTo(BASE + "/guide"))
                .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> client.guide(new GuideCall.Request("motive", "회사", "개발자", "",
                List.of(), List.of(), null)))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.TIMEOUT));
        server.verify();
    }

    @Test
    void 서버에_연결되지_않으면_GPU_꺼짐으로_본다() {
        HttpAiServerClient unreachable = HttpAiServerClient.create(properties("http://127.0.0.1:1",
                Duration.ofMillis(500), Duration.ofSeconds(1)));

        assertThatThrownBy(() -> unreachable.embed(List.of(EmbedItem.of("a")), EmbedPurpose.DOCUMENT))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.GPU_UNAVAILABLE));
        assertThat(unreachable.health().ready()).isFalse();
    }

    /** 응답을 주지 않는 서버를 띄워 읽기 시간 초과를 만든다. 다시 보내면 GPU 부하만 두 배가 되므로 재시도하지 않는다 (기획서 5.5) */
    @Test
    void 읽기_시간_초과는_다시_시도하지_않고_TIMEOUT() throws Exception {
        List<Socket> held = new CopyOnWriteArrayList<>();
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket silent = new ServerSocket(0)) {
            Thread accepter = new Thread(() -> {
                try {
                    while (true) {
                        held.add(silent.accept());   // 연결만 받고 응답은 보내지 않는다
                        connections.incrementAndGet();
                    }
                } catch (IOException closed) {
                    // 테스트가 끝나 소켓을 닫았다
                }
            });
            accepter.setDaemon(true);
            accepter.start();

            HttpAiServerClient stalling = HttpAiServerClient.create(
                    properties("http://127.0.0.1:" + silent.getLocalPort(), Duration.ofSeconds(2), Duration.ofMillis(300)));

            assertThatThrownBy(() -> stalling.embed(List.of(EmbedItem.of("a")), EmbedPurpose.DOCUMENT))
                    .isInstanceOfSatisfying(AiServerUnavailableException.class,
                            e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.TIMEOUT));
            assertThat(connections.get()).isEqualTo(1);
        } finally {
            for (Socket socket : held) {
                socket.close();
            }
        }
    }

    @Test
    void 엔드포인트마다_읽기_시간_제한이_다르다() {
        AiServerProperties properties = new AiServerProperties(BASE, false, Duration.ofSeconds(2),
                Duration.ofSeconds(30), Map.of(AiEndpoint.GUIDE, Duration.ofSeconds(120)));

        assertThat(properties.readTimeout(AiEndpoint.GUIDE)).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.readTimeout(AiEndpoint.MATCH)).isEqualTo(Duration.ofSeconds(30));   // 기본값
    }

    private static AiServerProperties properties(String baseUrl, Duration connect, Duration read) {
        return new AiServerProperties(baseUrl, false, connect, read, Map.of());
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
