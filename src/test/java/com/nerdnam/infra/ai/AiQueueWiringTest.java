package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.TestcontainersConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;

/**
 * 큐를 켠 앱 설정 (BE #66) — 세션 Redis 와 섞이지 않고, 작업 스레드는 가상 스레드이고, AI 호출이 큐로 가며
 * trace 가 이어진다.
 */
@SpringBootTest(properties = {"nerdnam.ai.stub=false", "nerdnam.ai.queue.enabled=true", "nerdnam.ai-job.sync=false",
        "management.tracing.sampling.probability=1.0"})
@Import(TestcontainersConfiguration.class)
class AiQueueWiringTest {

    /** 큐 전용 Redis — 세션 Redis(TestcontainersConfiguration)와 다른 컨테이너 */
    static final GenericContainer<?> QUEUE_REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    static {
        QUEUE_REDIS.start();
    }

    @DynamicPropertySource
    static void queue(DynamicPropertyRegistry registry) {
        registry.add("nerdnam.ai.queue.host", QUEUE_REDIS::getHost);
        registry.add("nerdnam.ai.queue.port", () -> QUEUE_REDIS.getMappedPort(6379));
        registry.add("nerdnam.ai.queue.consumer", () -> "wiring-test");
    }

    @AfterAll
    static void stop() {
        QUEUE_REDIS.stop();
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    AiServerClient ai;

    @Autowired
    ObservationRegistry observations;

    @Autowired
    Tracer tracer;

    @Autowired
    @Qualifier("aiJobExecutor")
    TaskExecutor executor;

    @Test
    void 세션은_원래_Redis_를_그대로_쓴다() {
        // 큐 연결을 RedisConnectionFactory 빈으로 만들면 세션 자동 설정이 어느 Redis 를 쓸지 바뀐다
        assertThat(context.getBeansOfType(RedisConnectionFactory.class).values()).singleElement()
                .isInstanceOfSatisfying(LettuceConnectionFactory.class,
                        f -> assertThat(f.getPort()).isNotEqualTo(QUEUE_REDIS.getMappedPort(6379)));
        assertThat(context.getBeansOfType(AiQueue.class)).hasSize(1);
    }

    @Test
    void AI_작업_스레드는_가상_스레드다() throws Exception {
        CompletableFuture<Boolean> virtual = new CompletableFuture<>();
        executor.execute(() -> virtual.complete(Thread.currentThread().isVirtual()));

        assertThat(virtual.get(5, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void AI_호출이_큐로_가고_메시지가_지금_trace_를_잇는다() throws Exception {
        String uri = "redis://" + QUEUE_REDIS.getHost() + ":" + QUEUE_REDIS.getMappedPort(6379);
        try (FakeAiWorker worker = new FakeAiWorker(uri, r -> List.of(FakeAiWorker.ok(r, "{\"lines\": []}")))) {
            String[] traceId = new String[1];
            Observation.createNotStarted("test.job", observations).observe(() -> {
                traceId[0] = tracer.currentSpan().context().traceId();
                ai.extractRequirements(new RequirementExtraction.Request(List.of()));
            });

            assertThat(worker.received).singleElement().satisfies(r -> {
                assertThat(r.stream()).isEqualTo("ai:req:llm");
                assertThat(r.fields().get("reply_to")).isEqualTo("ai:res:wiring-test");
                assertThat(r.fields().get("traceparent")).matches("00-" + traceId[0] + "-[0-9a-f]{16}-[0-9a-f]{2}");
            });
        }
    }
}
