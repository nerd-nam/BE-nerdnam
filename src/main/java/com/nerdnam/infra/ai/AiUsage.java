package com.nerdnam.infra.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * 요청 1건이 쓴 LLM 토큰 합계 (계약 C-04 v0.4.0 {@code usage}, BE-20).
 *
 * <p>AI 서버가 요청 안에서 여러 번 LLM 을 부를 수 있어(형식 오류 재요청, {@code /match} 는 요건마다)
 * 그 합계를 돌려준다. BE 는 {@code app.ai_call_log} 에 저장만 하고 금액은 계산하지 않는다 —
 * 단가가 바뀌므로 볼 때 {@code ai_model} 별 단가를 곱한다 (ADR-0007).
 *
 * <p><b>값이 없을 수 있다</b>: 연결 실패·읽기 시간 초과·요청 형식 422·{@code /embed}·오류 본문을 못 읽은 경우.
 * 그때는 세 칸 모두 null 로 저장한다 (0 과 구분해야 "안 쓴 것"과 "모르는 것"이 섞이지 않는다).
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiUsage(Integer inputTokens, Integer outputTokens, Integer llmCalls) {

    /** 가짜 응답(stub)과 LLM 을 부르지 않은 요청 */
    public static final AiUsage ZERO = new AiUsage(0, 0, 0);

    /**
     * 재시도까지 합친다 — 실패한 시도도 토큰을 썼다. 한쪽이 없으면 있는 쪽을 쓴다
     * (둘 다 없으면 null 을 돌려줘 "모름"을 유지한다).
     */
    @JsonIgnore
    public static AiUsage sum(AiUsage a, AiUsage b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new AiUsage(plus(a.inputTokens, b.inputTokens), plus(a.outputTokens, b.outputTokens),
                plus(a.llmCalls, b.llmCalls));
    }

    private static Integer plus(Integer x, Integer y) {
        return x == null ? y : (y == null ? x : x + y);
    }
}
