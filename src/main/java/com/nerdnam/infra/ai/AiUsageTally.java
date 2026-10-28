package com.nerdnam.infra.ai;

/**
 * 호출 1건이 쓴 토큰을 모으는 자리 (BE-20).
 *
 * <p><b>왜 필요한가:</b> {@code ai_call_log} 한 줄은 재시도를 포함한 호출 1건인데
 * ({@link HttpAiServerClient} 가 최대 2번 시도한다), <b>실패한 시도도 토큰을 쓴다</b>.
 * 마지막 응답의 {@code usage} 만 저장하면 재시도로 쓴 토큰이 빠진다.
 * 그래서 실패한 시도의 오류 본문에 담긴 {@code usage} 를 여기에 더해 두고,
 * {@link RecordingAiServerClient} 가 마지막 응답의 {@code usage} 와 합쳐 기록한다.
 *
 * <p>호출 스레드마다 따로 센다 (AI 작업은 스레드 하나가 처음부터 끝까지 맡는다).
 */
final class AiUsageTally {

    private static final ThreadLocal<AiUsage> FAILED_ATTEMPTS = new ThreadLocal<>();

    private AiUsageTally() {
    }

    /** 호출 하나를 시작한다 (앞 호출에서 남은 값을 지운다) */
    static void start() {
        FAILED_ATTEMPTS.remove();
    }

    /** 실패한 시도가 쓴 토큰을 더한다. 오류 본문에 usage 가 없으면 아무 일도 하지 않는다 */
    static void addFailedAttempt(AiUsage usage) {
        if (usage != null) {
            FAILED_ATTEMPTS.set(AiUsage.sum(FAILED_ATTEMPTS.get(), usage));
        }
    }

    /** 지금까지 모은 실패한 시도의 합을 꺼내고 비운다 */
    static AiUsage take() {
        AiUsage sum = FAILED_ATTEMPTS.get();
        FAILED_ATTEMPTS.remove();
        return sum;
    }
}
