package com.nerdnam.infra.ai;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * AI 클라이언트 고르기 — 스텁 / HTTP / 큐(nerdnam.ai.queue.enabled). 어느 것이든 호출 기록 감싸기를 씌운다.
 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiServerClient aiServerClient(AiServerProperties properties, AiQueueProperties queueProperties,
                                         ObjectProvider<AiQueue> queue, JdbcClient jdbc,
                                         ObjectProvider<ObservationRegistry> observations,
                                         ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
        ObservationRegistry registry = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
        AiServerClient client;
        if (properties.stub()) {
            client = new StubAiServerClient();
        } else {
            HttpAiServerClient http = HttpAiServerClient.create(properties, registry);
            client = queueProperties.enabled()
                    ? new QueueAiServerClient(queue.getObject(), queueProperties, http, registry,
                            traceparent(tracer.getIfAvailable(), propagator.getIfAvailable()))
                    : http;
        }
        return new RecordingAiServerClient(client, new JdbcAiCallLog(jdbc));
    }

    /** 새 AI 작업을 받아도 되는지 — 큐 경로에서만 큐에 쌓인 양을 본다 (BE #67) */
    @Bean
    public AiBacklog aiBacklog(AiServerProperties properties, AiQueueProperties queueProperties,
                               ObjectProvider<AiQueue> queue) {
        if (properties.stub() || !queueProperties.enabled()) {
            return AiBacklog.NONE;
        }
        return new QueueBacklog(queue.getObject(), queueProperties.maxBacklog());
    }

    /** 큐 연결과 결과 읽는 스레드. 끌 때 닫는다 (close 를 Spring 이 부른다) */
    @Bean
    @ConditionalOnBooleanProperty("nerdnam.ai.queue.enabled")
    public AiQueue aiQueue(AiQueueProperties properties) {
        return new AiQueue(properties);
    }

    /** 지금 span 을 W3C traceparent 로 — HTTP 호출이 헤더에 넣는 것과 같은 값 (BE #51) */
    static Supplier<String> traceparent(Tracer tracer, Propagator propagator) {
        if (tracer == null || propagator == null) {
            return () -> null;
        }
        return () -> {
            TraceContext context = tracer.currentTraceContext().context();
            if (context == null) {
                return null;
            }
            Map<String, String> carrier = new HashMap<>();
            propagator.inject(context, carrier, Map::put);
            return carrier.get("traceparent");
        };
    }
}
