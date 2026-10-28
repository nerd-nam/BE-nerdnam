package com.nerdnam.aijob.application;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 운영 지표 (BE #44). 관리 포트의 {@code /actuator/prometheus} 로 나간다.
 *
 * <p>label 은 유한한 값만 쓴다 — {@code member_id}·작업 id 는 label 로 쓰지 않는다(시계열이 무한히 늘어난다).
 * 그래서 작업 상태·청크 상태는 <b>개수</b>로만 센다.
 *
 * <p>DB 를 보는 지표는 Prometheus 가 긁을 때마다 질의한다. 모두 인덱스를 타는 가벼운 집계이고
 * (`ix_ai_job_member`, `ix_chunk_pending`), 긁는 주기는 15초 수준이다.
 */
@Component
class AiJobMetrics {

    AiJobMetrics(MeterRegistry registry, TaskExecutor aiJobExecutor, JdbcClient jdbc) {
        if (aiJobExecutor instanceof ThreadPoolTaskExecutor pool) {
            ThreadPoolExecutor executor = pool.getThreadPoolExecutor();
            Gauge.builder("nerdnam.aijob.queue.size", executor, e -> e.getQueue().size())
                    .description("AI 작업 큐에서 기다리는 작업 수")
                    .register(registry);
            Gauge.builder("nerdnam.aijob.threads.active", executor, ThreadPoolExecutor::getActiveCount)
                    .description("AI 작업을 실제로 돌리고 있는 스레드 수")
                    .register(registry);
            Gauge.builder("nerdnam.aijob.threads.max", executor, ThreadPoolExecutor::getMaximumPoolSize)
                    .description("AI 작업 스레드 수 상한")
                    .register(registry);
        }
        // DB 기준 작업 상태 — Pod 가 여러 대여도 전체를 본다 (큐 지표는 그 Pod 것이다)
        Gauge.builder("nerdnam.aijob.in.progress", jdbc,
                        j -> count(j, "SELECT count(*) FROM app.ai_job WHERE status = 'IN_PROGRESS'"))
                .description("진행 중으로 남아 있는 AI 작업 수 (모든 Pod)")
                .register(registry);
        Gauge.builder("nerdnam.aijob.failed.recent", jdbc,
                        j -> count(j, """
                                SELECT count(*) FROM app.ai_job
                                WHERE status = 'FAILED' AND finished_at >= now() - interval '5 minutes'
                                """))
                .description("최근 5분 안에 실패한 AI 작업 수")
                .register(registry);
        // 임베딩 대기 — 쌓이면 검색 근거가 비어 판정이 전부 미충족이 된다 (기획서 v1.18)
        Gauge.builder("nerdnam.chunk.pending", jdbc,
                        j -> count(j, "SELECT count(*) FROM rag.chunk WHERE embedding_status = 'PENDING'"))
                .description("임베딩을 기다리는 청크 수")
                .register(registry);
        Gauge.builder("nerdnam.chunk.pending.oldest.seconds", jdbc,
                        j -> count(j, """
                                SELECT coalesce(max(extract(epoch FROM now() - created_at)), 0)
                                FROM rag.chunk WHERE embedding_status = 'PENDING'
                                """))
                .description("가장 오래 기다린 대기 청크의 대기 시간(초)")
                .register(registry);
    }

    /** 지표 수집이 앱을 망치지 않게 한다 — DB 가 끊겨도 0 을 돌려준다 */
    private static double count(JdbcClient jdbc, String sql) {
        try {
            Double value = jdbc.sql(sql).query(Double.class).single();
            return value == null ? 0 : value;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
