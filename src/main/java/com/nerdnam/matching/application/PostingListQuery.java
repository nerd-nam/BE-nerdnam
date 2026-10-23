package com.nerdnam.matching.application;

import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 공고 목록 (S10, BE-18 뒷부분). 상태 컬럼을 따로 두지 않고 계산한다:
 * 분석이 없으면 작성 중(DRAFT), 있으면 가장 최근 분석의 상태 (02 문서 8장 S10).
 */
@Service
public class PostingListQuery {

    private final JdbcClient jdbc;

    public PostingListQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** @param status DRAFT(작성 중) / IN_PROGRESS / COMPLETED / FAILED. 충족 수는 분석 완료일 때만 */
    public record PostingSummary(Long id, String companyName, String positionName, String status, Long analysisId,
                                 Instant analyzedAt, Integer requiredMet, Integer requiredTotal, Integer preferredMet,
                                 Integer preferredPartial, Integer preferredTotal, Instant updatedAt) {
    }

    public List<PostingSummary> list(Long memberId) {
        return jdbc.sql("""
                        SELECT jp.id, jp.company_name, jp.position_name, jp.updated_at,
                               fa.id AS analysis_id, fa.status, fa.requested_at,
                               counts.required_met, counts.required_total,
                               counts.preferred_met, counts.preferred_partial, counts.preferred_total
                        FROM app.job_posting jp
                        LEFT JOIN LATERAL (
                            SELECT f.id, f.status, f.requested_at FROM app.fit_analysis f
                            WHERE f.job_posting_id = jp.id ORDER BY f.requested_at DESC, f.id DESC LIMIT 1
                        ) fa ON true
                        LEFT JOIN LATERAL (
                            SELECT count(*) FILTER (WHERE rm.requirement_type = 'REQUIRED' AND rm.final_verdict = 'MET') AS required_met,
                                   count(*) FILTER (WHERE rm.requirement_type = 'REQUIRED') AS required_total,
                                   count(*) FILTER (WHERE rm.requirement_type = 'PREFERRED' AND rm.final_verdict = 'MET') AS preferred_met,
                                   count(*) FILTER (WHERE rm.requirement_type = 'PREFERRED' AND rm.final_verdict = 'PARTIAL') AS preferred_partial,
                                   count(*) FILTER (WHERE rm.requirement_type = 'PREFERRED') AS preferred_total
                            FROM app.requirement_match rm WHERE rm.fit_analysis_id = fa.id AND fa.status = 'COMPLETED'
                        ) counts ON true
                        WHERE jp.member_id = :m
                        ORDER BY jp.updated_at DESC, jp.id DESC
                        """)
                .param("m", memberId)
                .query((rs, n) -> {
                    boolean completed = "COMPLETED".equals(rs.getString("status"));
                    return new PostingSummary(rs.getLong("id"), rs.getString("company_name"), rs.getString("position_name"),
                            rs.getString("status") == null ? "DRAFT" : rs.getString("status"),
                            (Long) rs.getObject("analysis_id"),
                            rs.getTimestamp("requested_at") == null ? null : rs.getTimestamp("requested_at").toInstant(),
                            completed ? rs.getInt("required_met") : null, completed ? rs.getInt("required_total") : null,
                            completed ? rs.getInt("preferred_met") : null, completed ? rs.getInt("preferred_partial") : null,
                            completed ? rs.getInt("preferred_total") : null, rs.getTimestamp("updated_at").toInstant());
                })
                .list();
    }
}
