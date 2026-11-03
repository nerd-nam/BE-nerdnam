package com.nerdnam.aijob.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * nerdnam.ai-job.*
 *
 * @param sync         true 면 별도 스레드 대신 요청 스레드에서 바로 실행한다 (테스트용)
 * @param staleAfter   이 시간이 지나도 진행 중인 작업은 실패(TIMEOUT)로 정리한다
 * @param threads      AI 작업 스레드 수 (HTTP 경로)
 * @param queueThreads AI 호출을 큐로 보낼 때(nerdnam.ai.queue.enabled)의 작업 스레드 수. 이때는 가상 스레드라
 *                     결과를 기다리는 동안 OS 스레드를 쥐지 않는다 — 속도는 AI 가 큐에서 꺼내는 양이 정하고,
 *                     이 값은 한 Pod 가 동시에 맡는 작업 수의 상한이다 (ADR-0009, BE #66)
 */
@ConfigurationProperties(prefix = "nerdnam.ai-job")
public record AiJobProperties(boolean sync, Duration staleAfter, int threads, int queueThreads) {

    public AiJobProperties {
        staleAfter = staleAfter == null ? Duration.ofMinutes(10) : staleAfter;
        threads = threads <= 0 ? 4 : threads;
        queueThreads = queueThreads <= 0 ? 64 : queueThreads;
    }
}
