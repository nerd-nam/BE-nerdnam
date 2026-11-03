package com.nerdnam.infra.ai;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 지금 스레드에서 부르는 AI 호출이 누구의 무엇을 위한 것인지 (ai_call_log 의 member_id, target_*).
 * AI 작업 실행기가 작업을 돌리는 동안 채워 둔다. 비어 있으면 기록에 null 로 남는다.
 *
 * @param jobAcceptedAt AI 작업을 접수한 시각. 큐 경로는 작업 하나가 AI 를 여러 번 불러도 이 시각부터
 *                      {@code job-timeout} 안에 끝낸다 (BE #66). 작업이 아닌 호출(문서 임베딩)은 null
 */
public record AiCallContext(Long memberId, String targetType, Long targetId, Instant jobAcceptedAt) {

    public AiCallContext(Long memberId, String targetType, Long targetId) {
        this(memberId, targetType, targetId, null);
    }

    private static final ThreadLocal<AiCallContext> CURRENT = new ThreadLocal<>();

    public static <T> T with(AiCallContext context, Supplier<T> work) {
        AiCallContext previous = CURRENT.get();
        CURRENT.set(context);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    /** 지금 스레드의 AI 작업 접수 시각 (작업 밖이면 비어 있다) */
    public static Optional<Instant> currentJobAcceptedAt() {
        return Optional.ofNullable(current().jobAcceptedAt());
    }

    static AiCallContext current() {
        AiCallContext context = CURRENT.get();
        return context == null ? new AiCallContext(null, null, null) : context;
    }
}
