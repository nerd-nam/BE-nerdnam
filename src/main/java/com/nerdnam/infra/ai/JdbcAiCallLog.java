package com.nerdnam.infra.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 기록 실패가 본 작업을 망치지 않도록 예외를 삼키고 로그만 남긴다 */
class JdbcAiCallLog implements AiCallLog {

    private static final Logger log = LoggerFactory.getLogger(JdbcAiCallLog.class);

    private final JdbcClient jdbc;

    JdbcAiCallLog(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(AiCallContext context, String operation, String aiModel, String promptVersion,
                       boolean success, String errorCode, long latencyMs) {
        try {
            jdbc.sql("""
                            INSERT INTO app.ai_call_log
                                (member_id, operation, target_type, target_id, ai_model, prompt_version, success, error_code, latency_ms)
                            VALUES (:memberId, :operation, :targetType, :targetId, :aiModel, :promptVersion, :success, :errorCode, :latency)
                            """)
                    .param("memberId", context.memberId())
                    .param("operation", operation)
                    .param("targetType", context.targetType())
                    .param("targetId", context.targetId())
                    .param("aiModel", aiModel)
                    .param("promptVersion", promptVersion)
                    .param("success", success)
                    .param("errorCode", errorCode)
                    .param("latency", (int) Math.min(Integer.MAX_VALUE, latencyMs))
                    .update();
        } catch (RuntimeException e) {
            log.warn("Failed to record AI call log ({}): {}", operation, e.getMessage());
        }
    }
}
