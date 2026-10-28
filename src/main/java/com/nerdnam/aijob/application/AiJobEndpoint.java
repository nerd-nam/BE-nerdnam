package com.nerdnam.aijob.application;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 배포 절차용 관리 endpoint (BE #45). <b>관리 포트에서만</b> 열린다 (BE #44, Ingress 에 넣지 않는다).
 *
 * <pre>
 * GET  /actuator/aijob                      지금 상태 (접수 중지 여부, 큐·실행 중 개수)
 * POST /actuator/aijob  {"state":"drain"}   새 작업 접수 중지 + readiness 내림
 * POST /actuator/aijob  {"state":"resume"}  접수 재개
 * </pre>
 *
 * <p>배포 절차: {@code drain} → {@code inFlight} 와 {@code queued} 가 0 이 될 때까지 폴링 → 교체.
 * 0 을 못 기다리고 내려가도 끝내지 못한 작업은 종료 때 실패로 정리된다
 * ({@code AiJobs.failUnfinishedOnShutdown}) — 사용자는 "다시 시도"를 본다.
 */
@Component
@Endpoint(id = "aijob")
public class AiJobEndpoint {

    private final AiJobDrain drain;
    private final TaskExecutor executor;

    AiJobEndpoint(AiJobDrain drain, @Qualifier("aiJobExecutor") TaskExecutor executor) {
        this.drain = drain;
        this.executor = executor;
    }

    @ReadOperation
    public Map<String, Object> status() {
        return Map.of("draining", drain.draining(),
                "inFlight", drain.inFlight(),
                "queued", queued(),
                "active", active(),
                "drained", drain.inFlight() == 0 && queued() == 0 && active() == 0);
    }

    /** @param state {@code drain} 또는 {@code resume} */
    @WriteOperation
    public Map<String, Object> change(String state) {
        if ("drain".equalsIgnoreCase(state)) {
            drain.drain();
        } else if ("resume".equalsIgnoreCase(state)) {
            drain.resume();
        } else {
            return Map.of("error", "state 는 drain 또는 resume 이어야 해요", "given", String.valueOf(state));
        }
        return status();
    }

    private int queued() {
        return executor instanceof ThreadPoolTaskExecutor pool ? pool.getThreadPoolExecutor().getQueue().size() : 0;
    }

    private int active() {
        return executor instanceof ThreadPoolTaskExecutor pool
                ? pool.getThreadPoolExecutor().getActiveCount() : 0;
    }
}
