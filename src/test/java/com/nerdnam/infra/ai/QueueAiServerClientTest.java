package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

/**
 * AI 호출을 Redis Streams 큐로 보내는 클라이언트 (ADR-0009, BE #66). 실제 Redis 에 가짜 AI 작업자를 붙여 본다.
 */
class QueueAiServerClientTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static final String TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    private static RedisClient admin;
    private static StatefulRedisConnection<String, String> redis;

    private AiQueue queue;
    private FakeAiWorker worker;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        admin = RedisClient.create(uri());
        redis = admin.connect();
    }

    @AfterAll
    static void stopRedis() {
        redis.close();
        admin.shutdown();
        REDIS.stop();
    }

    @BeforeEach
    void clean() {
        redis.sync().flushall();
        AiUsageTally.start();
    }

    @AfterEach
    void close() throws InterruptedException {
        if (worker != null) {
            worker.close();
        }
        if (queue != null) {
            queue.close();
        }
    }

    @Test
    void 큐에_넣고_결과를_받아_돌려준다() {
        worker = new FakeAiWorker(uri(), r -> List.of(FakeAiWorker.ok(r, """
                {"results": [{"id": 7, "verdict": "met", "evidence_chunk_ids": [3], "reason": "근거"}],
                 "model": "nova", "prompt_version": "p3", "usage": {"input_tokens": 10, "output_tokens": 2, "llm_calls": 1},
                 "added_later": true}
                """)));
        QueueAiServerClient client = client(props(Duration.ofMinutes(1), Duration.ofMinutes(1)));

        MatchCall.Result result = client.match(new MatchCall.Request(List.of(new MatchCall.Requirement(
                7L, "required", "Java 3년", List.of("Java"), 3, List.of(new MatchCall.Candidate(3L, "episode", "자바"))))));

        assertThat(result.results()).singleElement().satisfies(j -> {
            assertThat(j.verdict()).isEqualTo("met");
            assertThat(j.evidenceChunkIds()).containsExactly(3L);
        });
        assertThat(result.usage()).isEqualTo(new AiUsage(10, 2, 1));
        FakeAiWorker.Received sent = worker.received.getFirst();
        assertThat(sent.stream()).isEqualTo("ai:req:llm");
        assertThat(sent.fields())
                .containsEntry("op", "match")
                .containsEntry("traceparent", TRACEPARENT)
                .containsEntry("reply_to", queue.replyStream())
                .containsKeys("id", "enqueued_at");
        // body 는 지금 HTTP 요청 JSON 그대로 (snake_case)
        assertThat(sent.fields().get("body")).contains("\"min_years\":3", "\"chunk_id\":3", "\"chunk_type\":\"episode\"");
    }

    @Test
    void 요건_임베딩은_embed_문서_임베딩은_embed_bg_로_보낸다() {
        worker = new FakeAiWorker(uri(), r -> List.of(FakeAiWorker.ok(r, """
                {"vectors": [[0.1, 0.2]], "embedding_model": "titan", "dim": 2}
                """)));
        QueueAiServerClient client = client(props(Duration.ofMinutes(1), Duration.ofMinutes(1)));

        client.embed(List.of(EmbedItem.of("요건")), EmbedPurpose.QUERY);
        Embeddings doc = client.embed(List.of(EmbedItem.of("사례")), EmbedPurpose.DOCUMENT);

        assertThat(worker.received).extracting(FakeAiWorker.Received::stream)
                .containsExactly("ai:req:embed", "ai:req:embed-bg");
        assertThat(worker.received.getLast().fields().get("body")).contains("\"purpose\":\"document\"");
        assertThat(doc.vectors()).singleElement().satisfies(v -> assertThat(v).containsExactly(0.1f, 0.2f));
    }

    @Test
    void 실패_결과는_AI_ERROR_이고_실패한_호출의_토큰도_합친다() {
        worker = new FakeAiWorker(uri(), r -> List.of(Map.of("id", r.fields().get("id"), "status", "502",
                "detail", "응답 형식 오류", "usage", "{\"input_tokens\": 40, \"output_tokens\": 5, \"llm_calls\": 2}")));
        QueueAiServerClient client = client(props(Duration.ofMinutes(1), Duration.ofMinutes(1)));

        assertThatThrownBy(() -> client.gapGuide(new GapGuideCall.Request(List.of(), List.of())))
                .isInstanceOfSatisfying(AiServerUnavailableException.class, e -> {
                    assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.AI_ERROR);
                    assertThat(e.operation()).isEqualTo("gap-guide");
                });
        assertThat(AiUsageTally.take()).isEqualTo(new AiUsage(40, 5, 2));
    }

    @Test
    void 결과가_없으면_같은_id_로_다시_넣는다() {
        // 첫 메시지는 사라졌다(큐 Redis 재시작). 다시 넣은 것에만 답한다
        worker = new FakeAiWorker(uri(), r -> worker.countWithId(r.fields().get("id")) < 2 ? List.of()
                : List.of(FakeAiWorker.ok(r, "{\"lines\": []}")));
        QueueAiServerClient client = client(props(Duration.ofMillis(300), Duration.ofSeconds(10)));

        client.extractRequirements(new RequirementExtraction.Request(List.of()));

        assertThat(worker.received).hasSize(2);
        assertThat(worker.received.get(1).fields()).isEqualTo(worker.received.get(0).fields());   // 같은 id·같은 본문
    }

    @Test
    void 끝내_결과가_없으면_TIMEOUT_으로_끝낸다() {
        worker = new FakeAiWorker(uri(), r -> List.of());
        QueueAiServerClient client = client(props(Duration.ofMillis(200), Duration.ofMillis(700)));

        assertThatThrownBy(() -> client.extractRequirements(new RequirementExtraction.Request(List.of())))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.TIMEOUT));
        assertThat(worker.received.size()).isBetween(2, 5);   // 처음 + 200ms 마다 다시
    }

    @Test
    void 같은_결과가_두_번_와도_한_번만_반영하고_다음_호출에_섞이지_않는다() {
        // AI 는 최소 1회 처리라 같은 id 의 결과가 두 번 올 수 있다
        worker = new FakeAiWorker(uri(), r -> {
            String body = "{\"lines\": [], \"model\": \"" + r.fields().get("id") + "\"}";
            return List.of(FakeAiWorker.ok(r, body), FakeAiWorker.ok(r, body));
        });
        QueueAiServerClient client = client(props(Duration.ofMinutes(1), Duration.ofMinutes(1)));

        String first = client.extractRequirements(new RequirementExtraction.Request(List.of())).model();
        String second = client.extractRequirements(new RequirementExtraction.Request(List.of())).model();

        assertThat(first).isEqualTo(worker.received.get(0).fields().get("id"));
        assertThat(second).isEqualTo(worker.received.get(1).fields().get("id"));
        // 읽은 결과는 지운다(이력 원문이 들어 있다). 키에는 만료를 건다
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(redis.sync().xlen(queue.replyStream())).isZero();
            assertThat(redis.sync().ttl(queue.replyStream())).isPositive();
        });
    }

    @Test
    void 큐_Redis_에_닿지_못하면_GPU_UNAVAILABLE_로_바로_끝낸다() {
        AiQueueProperties unreachable = new AiQueueProperties(true, "localhost", 1, null, null,
                Duration.ofSeconds(1), "test-" + UUID.randomUUID(), Duration.ofMinutes(1), Duration.ofMinutes(1), null);
        queue = new AiQueue(unreachable);
        QueueAiServerClient client = new QueueAiServerClient(queue, unreachable, new StubAiServerClient(),
                ObservationRegistry.NOOP, () -> TRACEPARENT);

        assertThatThrownBy(() -> client.extractRequirements(new RequirementExtraction.Request(List.of())))
                .isInstanceOfSatisfying(AiServerUnavailableException.class,
                        e -> assertThat(e.reason()).isEqualTo(AiServerUnavailableException.Reason.GPU_UNAVAILABLE));
    }

    private QueueAiServerClient client(AiQueueProperties properties) {
        queue = new AiQueue(properties);
        return new QueueAiServerClient(queue, properties, new StubAiServerClient(), ObservationRegistry.NOOP,
                () -> TRACEPARENT);
    }

    private static AiQueueProperties props(Duration resendAfter, Duration waitTimeout) {
        return new AiQueueProperties(true, REDIS.getHost(), REDIS.getMappedPort(6379), null, null, null,
                "test-" + UUID.randomUUID(), resendAfter, waitTimeout, null);
    }

    private static String uri() {
        return "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379);
    }
}
