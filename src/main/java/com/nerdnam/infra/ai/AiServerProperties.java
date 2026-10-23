package com.nerdnam.infra.ai;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버(FastAPI) 연결 설정 — application.yml 의 nerdnam.ai.*
 *
 * @param baseUrl        AI 서버 주소
 * @param stub           true 면 실제 서버 대신 {@link StubAiServerClient}를 쓴다 (local 기본값)
 * @param connectTimeout 연결 시간 제한. GPU 서버가 꺼져 있으면 빨리 실패하도록 짧게 (ADR-0001: 2초)
 * @param readTimeout    응답 시간 제한의 기본값 (readTimeouts 에 없는 엔드포인트)
 * @param readTimeouts   엔드포인트별 응답 시간 제한. 생성 단계마다 걸리는 시간이 달라 따로 둔다 (기획서 5.5)
 */
@ConfigurationProperties(prefix = "nerdnam.ai")
public record AiServerProperties(String baseUrl, boolean stub, Duration connectTimeout, Duration readTimeout,
                                 Map<AiEndpoint, Duration> readTimeouts) {

    public AiServerProperties {
        readTimeouts = readTimeouts == null ? Map.of() : Map.copyOf(readTimeouts);
    }

    /** 이 엔드포인트의 응답 시간 제한. 따로 정하지 않았으면 기본값 */
    public Duration readTimeout(AiEndpoint endpoint) {
        return readTimeouts.getOrDefault(endpoint, readTimeout);
    }
}
