package com.nerdnam.infra.ai;

import java.util.function.Supplier;

/**
 * 지금 스레드에서 부르는 AI 호출이 누구의 무엇을 위한 것인지 (ai_call_log 의 member_id, target_*).
 * AI 작업 실행기가 작업을 돌리는 동안 채워 둔다. 비어 있으면 기록에 null 로 남는다.
 */
public record AiCallContext(Long memberId, String targetType, Long targetId) {

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

    static AiCallContext current() {
        AiCallContext context = CURRENT.get();
        return context == null ? new AiCallContext(null, null, null) : context;
    }
}
