package com.nerdnam.aijob.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * nerdnam.ai-job.*
 *
 * @param sync       true 면 별도 스레드 대신 요청 스레드에서 바로 실행한다 (테스트용)
 * @param staleAfter 이 시간이 지나도 진행 중인 작업은 실패(TIMEOUT)로 정리한다
 * @param threads    AI 작업 스레드 수
 */
@ConfigurationProperties(prefix = "nerdnam.ai-job")
public record AiJobProperties(boolean sync, Duration staleAfter, int threads) {

    public AiJobProperties {
        staleAfter = staleAfter == null ? Duration.ofMinutes(10) : staleAfter;
        threads = threads <= 0 ? 4 : threads;
    }
}
