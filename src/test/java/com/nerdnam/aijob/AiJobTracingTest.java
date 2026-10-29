package com.nerdnam.aijob;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.aijob.application.AiJobConfig;
import com.nerdnam.aijob.application.AiJobProperties;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * AI 작업 스레드로 trace 가 이어지는지 (BE #51).
 *
 * <p>작업은 접수 요청 스레드가 아니라 실행기 스레드에서 돈다. 실행기가 context 를 넘기지 않으면
 * 작업 span 이 부모 없는 고아가 되어 "웹 요청 → BE → AI → Bedrock" 이 한 trace 로 이어지지 않는다.
 * 이슈가 "executor 로 넘어갈 때 전파가 실제로 되는지 테스트로 확인" 해 달라고 한 부분이다.
 */
class AiJobTracingTest {

    @Test
    void 접수_요청의_observation이_작업_스레드로_이어진다() throws Exception {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        TaskExecutor executor = new AiJobConfig().aiJobExecutor(
                new AiJobProperties(false, Duration.ofMinutes(20), 1));
        try {
            Observation request = Observation.start("http.server.requests", registry);
            CompletableFuture<Observation> seenInWorker = new CompletableFuture<>();
            String[] workerThread = new String[1];
            try (Observation.Scope ignored = request.openScope()) {
                executor.execute(() -> {
                    workerThread[0] = Thread.currentThread().getName();
                    seenInWorker.complete(registry.getCurrentObservation());
                });
            }
            request.stop();

            assertThat(seenInWorker.get(5, TimeUnit.SECONDS)).isSameAs(request);
            assertThat(workerThread[0]).startsWith("ai-job-");   // 정말 다른 스레드에서 확인했다
        } finally {
            ((ThreadPoolTaskExecutor) executor).shutdown();
        }
    }
}
