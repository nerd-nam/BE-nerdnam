package com.nerdnam.infra.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 서버(FastAPI) 연결 설정 — application.yml 의 nerdnam.ai.*
 *
 * @param baseUrl        AI 서버 주소
 * @param stub           true 면 실제 서버 대신 {@link StubAiServerClient}를 쓴다 (local 기본값)
 * @param connectTimeout 연결 시간 제한. GPU 서버가 꺼져 있으면 빨리 실패하도록 짧게 (ADR-0001: 2초)
 * @param readTimeout    응답 시간 제한
 */
@ConfigurationProperties(prefix = "nerdnam.ai")
public record AiServerProperties(String baseUrl, boolean stub, Duration connectTimeout, Duration readTimeout) {
}
