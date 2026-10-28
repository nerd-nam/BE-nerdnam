package com.nerdnam.infra.ai;

/** AI 호출 한 번의 기록 (app.ai_call_log) — 비용·지연·KPI 측정용 */
public interface AiCallLog {

    /**
     * @param operation RESTRUCTURE, EXTRACT_REQUIREMENTS, EMBED, MATCH, GAP_GUIDE, GUIDE
     * @param usage     이 호출이 쓴 LLM 토큰 (재시도 포함한 합계). 모르면 null — /embed·연결 실패·시간 초과 등 (BE-20)
     */
    void record(AiCallContext context, String operation, String aiModel, String promptVersion,
                boolean success, String errorCode, long latencyMs, AiUsage usage);
}
