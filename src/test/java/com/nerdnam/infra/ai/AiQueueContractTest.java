package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lettuce.core.RedisClient;
import io.micrometer.observation.ObservationRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * 큐 메시지 계약 (C-04 v0.5.0 {@code QueueRequest}·{@code QueueResult}, ADR-0009, BE #66).
 *
 * <p>큐는 경로가 아니라 스트림 메시지라 C-04 에는 형식만 {@code components.schemas} 에 있다. 실제 Redis 에
 * 가짜 AI 작업자를 붙여, BE 가 넣는 메시지와 읽는 결과가 그 스키마와 같은지 본다. C-04 복사본이 SPEC 원본과
 * 같은지는 {@link ApiAiContractTest} 가 본다.
 */
class AiQueueContractTest {

    private static final JsonNode SCHEMAS = readYaml("/contracts/api-ai.openapi.yaml").path("components").path("schemas");
    private static final JsonNode REQUEST = SCHEMAS.path("QueueRequest");
    private static final JsonNode RESULT = SCHEMAS.path("QueueResult");
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @BeforeAll
    static void start() {
        REDIS.start();
    }

    @AfterAll
    static void stop() {
        REDIS.stop();
    }

    @Test
    void 요청_메시지의_필드와_필수_목록이_QueueRequest_와_같다() throws Exception {
        assertThat(REQUEST.isMissingNode()).as("C-04 QueueRequest").isFalse();
        Map<String, String> withTrace = sent("00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01").getFirst().fields();
        Map<String, String> withoutTrace = sent(null).getFirst().fields();

        assertThat(withTrace.keySet()).containsExactlyInAnyOrderElementsOf(names(REQUEST.path("properties")));
        assertThat(QueueAiServerClient.REQUEST_FIELDS).containsExactlyInAnyOrderElementsOf(names(REQUEST.path("properties")));
        // traceparent 는 선택이다 — span 이 없으면 빼고 보낸다. 필수는 항상 있다
        assertThat(withoutTrace.keySet()).containsExactlyInAnyOrderElementsOf(strings(REQUEST.path("required")));

        // id = {op}:{uuid}, op = HTTP 경로에서 / 뺀 것, enqueued_at = ISO-8601 UTC, reply_to = ai:res:{BE Pod}
        assertThat(withTrace.get("op")).isEqualTo(AiEndpoint.REQUIREMENTS_EXTRACT.path().substring(1));
        assertThat(withTrace.get("id")).matches(Pattern.quote(withTrace.get("op")) + ":[0-9a-f-]{36}");
        assertThat(Instant.parse(withTrace.get("enqueued_at"))).isNotNull();
        assertThat(withTrace.get("enqueued_at")).endsWith("Z");
        assertThat(withTrace.get("reply_to")).startsWith("ai:res:");
    }

    @Test
    void op_마다_QueueRequest_설명의_스트림으로_간다() throws Exception {
        // 설명: XADD ai:req:llm(LLM op) / ai:req:embed(embed, purpose=query) / ai:req:embed-bg(embed, purpose=document)
        Set<String> ops = strings(REQUEST.path("properties").path("op").path("enum"));
        String description = REQUEST.path("description").asString();
        assertThat(description).contains(QueueAiServerClient.LLM, QueueAiServerClient.EMBED + "(embed, purpose=query)",
                QueueAiServerClient.EMBED_BG + "(embed, purpose=document)");

        List<FakeAiWorker.Received> sent = calls(null,
                c -> c.extractRequirements(new RequirementExtraction.Request(List.of())),
                c -> c.restructure(new RestructureRequest(RestructureRequest.MODE_INITIAL, "제목", "원문", List.of(), null,
                        null, null, null, null)),
                c -> c.match(new MatchCall.Request(List.of())),
                c -> c.gapGuide(new GapGuideCall.Request(List.of(), List.of())),
                c -> c.guide(new GuideCall.Request("motive", "회사", "직무", null, List.of(), List.of(), null)),
                c -> c.embed(List.of(EmbedItem.of("요건")), EmbedPurpose.QUERY),
                c -> c.embed(List.of(EmbedItem.of("사례")), EmbedPurpose.DOCUMENT));

        // 계약의 op 를 BE 가 모두 쓰고, 그 밖의 op 는 보내지 않는다
        assertThat(sent).extracting(r -> r.fields().get("op")).containsOnlyElementsOf(ops);
        assertThat(sent.stream().map(r -> r.fields().get("op")).collect(Collectors.toSet())).isEqualTo(ops);
        // embed 가 아닌 op 는 모두 LLM 스트림
        assertThat(sent).filteredOn(r -> !r.fields().get("op").equals("embed"))
                .extracting(FakeAiWorker.Received::stream).containsOnly(QueueAiServerClient.LLM);
        assertThat(sent).filteredOn(r -> r.fields().get("op").equals("embed"))
                .extracting(r -> r.stream() + " " + r.fields().get("body").contains("\"purpose\":\"document\""))
                .containsExactly(QueueAiServerClient.EMBED + " false", QueueAiServerClient.EMBED_BG + " true");
    }

    @Test
    void 결과_메시지의_필드와_필수_목록이_QueueResult_와_같다() {
        assertThat(RESULT.isMissingNode()).as("C-04 QueueResult").isFalse();
        assertThat(AiQueue.RESULT_FIELDS).containsExactlyInAnyOrderElementsOf(names(RESULT.path("properties")));
        assertThat(strings(RESULT.path("required"))).containsExactlyInAnyOrder("id", "status");
        assertThat(strings(RESULT.path("properties").path("status").path("enum"))).containsExactlyInAnyOrder("200", "422", "502");
    }

    @Test
    void 결과_status_200_은_HTTP_응답처럼_읽고_422_502_는_AI_ERROR() throws Exception {
        // 필수(id·status)만 있는 실패 결과도 받는다. usage 는 JSON 문자열이다
        try (Fixture f = new Fixture(null, r -> List.of(Map.of("id", r.fields().get("id"), "status", "200",
                "body", "{\"lines\": [], \"model\": \"nova\", \"prompt_version\": \"p2\"}",
                "usage", "{\"input_tokens\": 1, \"output_tokens\": 1, \"llm_calls\": 1}")))) {
            assertThat(f.client.extractRequirements(new RequirementExtraction.Request(List.of())).model()).isEqualTo("nova");
        }
        for (String status : List.of("422", "502")) {
            try (Fixture f = new Fixture(null, r -> List.of(Map.of("id", r.fields().get("id"), "status", status)))) {
                assertThatThrownBy(() -> f.client.match(new MatchCall.Request(List.of())))
                        .isInstanceOfSatisfying(AiServerUnavailableException.class,
                                e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR));
            }
        }
    }

    // ------------------------------------------------------------------ 계약 읽기

    private static JsonNode readYaml(String resource) {
        try (InputStream in = AiQueueContractTest.class.getResourceAsStream(resource)) {
            return YAMLMapper.builder().build().readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(resource, e);
        }
    }

    private static Set<String> names(JsonNode properties) {
        return Set.copyOf(properties.propertyNames());
    }

    private static Set<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(v -> values.add(v.asString()));
        return Set.copyOf(values);
    }

    // ------------------------------------------------------------------ 보내 보기

    private static List<FakeAiWorker.Received> sent(String traceparent) throws Exception {
        return calls(traceparent, c -> c.extractRequirements(new RequirementExtraction.Request(List.of())));
    }

    /** 호출마다 가짜 작업자가 502 로 답한다(필드·스트림만 본다) */
    @SafeVarargs
    private static List<FakeAiWorker.Received> calls(String traceparent, Consumer<QueueAiServerClient>... calls)
            throws Exception {
        try (Fixture f = new Fixture(traceparent, r -> List.of(Map.of("id", r.fields().get("id"), "status", "502")))) {
            Arrays.stream(calls).forEach(call -> {
                try {
                    call.accept(f.client);
                } catch (AiServerUnavailableException expected) {
                    // 502 → AI_ERROR
                }
            });
            return new ArrayList<>(f.worker.received);
        }
    }

    private static final class Fixture implements AutoCloseable {

        final AiQueue queue;
        final FakeAiWorker worker;
        final QueueAiServerClient client;

        Fixture(String traceparent, Function<FakeAiWorker.Received, List<Map<String, String>>> reply) {
            String uri = "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
            // 앞 테스트가 넣은 메시지를 가짜 작업자가 다시 읽지 않게 비운다
            RedisClient admin = RedisClient.create(uri);
            try (var c = admin.connect()) {
                c.sync().flushall();
            } finally {
                admin.shutdown();
            }
            AiQueueProperties p = new AiQueueProperties(true, REDIS.getHost(), REDIS.getMappedPort(6379), null, null,
                    null, "contract-" + UUID.randomUUID(), Duration.ofMinutes(1), Duration.ofSeconds(30), null, null, null);
            this.queue = new AiQueue(p);
            this.worker = new FakeAiWorker(uri, reply);
            this.client = new QueueAiServerClient(queue, p, new StubAiServerClient(), ObservationRegistry.NOOP,
                    () -> traceparent);
        }

        @Override
        public void close() throws InterruptedException {
            worker.close();
            queue.close();
        }
    }
}
