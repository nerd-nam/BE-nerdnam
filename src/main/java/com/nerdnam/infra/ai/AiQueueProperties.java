package com.nerdnam.infra.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 작업 큐(Redis Streams) 설정 — application.yml 의 nerdnam.ai.queue.* (ADR-0009, BE #66)
 *
 * <p>켜면 AI 호출을 HTTP 대신 큐로 보낸다. AI 서버가 한도 안에서 꺼내 가고, BE 작업 스레드(가상 스레드)는
 * 결과가 올 때까지 기다린다. 끄면 지금처럼 HTTP 로 부른다 — 문제가 생기면 이 값만 되돌린다.
 *
 * @param enabled     true 면 큐로 보낸다 (기본 false)
 * @param host        큐 전용 Redis 주소. 세션 Redis 와 다른 서버다 — 큐가 밀려도 로그인이 막히지 않게 (ADR-0009)
 * @param port        큐 Redis 포트
 * @param username    ACL 사용자 (없으면 비운다)
 * @param password    ACL 비밀번호 (없으면 비운다)
 * @param timeout     명령 하나의 제한 시간
 * @param consumer    결과를 받을 이름. 결과 스트림은 {@code ai:res:{consumer}} 다. 비우면 Pod 이름(HOSTNAME)
 * @param resendAfter 이만큼 결과가 없으면 같은 id 로 다시 넣는다. 큐 Redis 재시작으로 메시지가 사라졌을 때를 위해서다
 * @param waitTimeout 호출 하나를 이보다 오래 기다리지 않는다 → TIMEOUT. 화면이 "진행 중"에 끝없이 남지 않게
 * @param resultTtl   결과 스트림 키의 만료. 내려간 Pod 의 스트림이 남지 않게 읽을 때마다 늘린다
 */
@ConfigurationProperties(prefix = "nerdnam.ai.queue")
public record AiQueueProperties(boolean enabled, String host, Integer port, String username, String password,
                                Duration timeout, String consumer, Duration resendAfter, Duration waitTimeout,
                                Duration resultTtl) {

    public AiQueueProperties {
        host = blank(host) ? "localhost" : host;
        port = port == null ? 6379 : port;
        timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        consumer = blank(consumer) ? hostname() : consumer;
        resendAfter = resendAfter == null ? Duration.ofMinutes(5) : resendAfter;
        waitTimeout = waitTimeout == null ? Duration.ofMinutes(20) : waitTimeout;
        resultTtl = resultTtl == null ? Duration.ofHours(1) : resultTtl;
    }

    /** 결과를 받을 스트림 — 요청 메시지의 {@code reply_to} */
    public String replyStream() {
        return "ai:res:" + consumer;
    }

    static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String hostname() {
        String name = System.getenv("HOSTNAME");
        return blank(name) ? "local-" + ProcessHandle.current().pid() : name;
    }
}
