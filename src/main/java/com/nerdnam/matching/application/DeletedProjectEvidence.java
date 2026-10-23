package com.nerdnam.matching.application;

import com.nerdnam.matching.domain.EvidenceLabel;
import com.nerdnam.matching.domain.EvidenceSourceType;
import com.nerdnam.project.domain.ProjectDeleted;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 이전 분석은 유지하되 삭제한 프로젝트의 근거 원문은 이름으로 바꾼다. */
@Component
public class DeletedProjectEvidence {
    private final JdbcClient jdbc;

    public DeletedProjectEvidence(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener
    public void on(ProjectDeleted event) {
        var episodes = event.episodeIds().isEmpty() ? java.util.List.of(-1L) : event.episodeIds();
        var rows = jdbc.sql("""
                SELECT e.id, e.source_type, e.snippet FROM app.match_evidence e
                JOIN app.requirement_match m ON m.id = e.requirement_match_id
                JOIN app.fit_analysis a ON a.id = m.fit_analysis_id
                WHERE a.member_id = :member AND NOT e.source_deleted AND
                ((e.source_type = 'PROJECT_SUMMARY' AND e.source_id = :project)
                 OR (e.source_type = 'EPISODE' AND e.source_id IN (:episodes)))
                """).param("member", event.memberId()).param("project", event.projectId()).param("episodes", episodes)
                .query((rs, n) -> new Label(rs.getLong(1), EvidenceLabel.of(
                        EvidenceSourceType.valueOf(rs.getString(2)), rs.getString(3)))).list();
        rows.forEach(row -> jdbc.sql("UPDATE app.match_evidence SET snippet = :label, source_deleted = true WHERE id = :id")
                .param("label", row.label()).param("id", row.id()).update());
    }

    private record Label(long id, String label) { }
}
