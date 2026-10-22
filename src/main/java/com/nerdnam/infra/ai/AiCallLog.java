package com.nerdnam.infra.ai;

/** AI 호출 한 번의 기록 (app.ai_call_log) — 비용·지연·KPI 측정용 */
public interface AiCallLog {

    /** @param operation RESTRUCTURE, EXTRACT_REQUIREMENTS, EMBED, MATCH, GAP_GUIDE, GUIDE */
    void record(AiCallContext context, String operation, String aiModel, String promptVersion,
                boolean success, String errorCode, long latencyMs);
}
