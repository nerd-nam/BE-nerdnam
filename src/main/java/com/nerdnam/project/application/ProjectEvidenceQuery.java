package com.nerdnam.project.application;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 다른 모듈이 근거의 이동 위치를 정할 때 쓰는 공개 조회. 소유한 프로젝트의 현재 상태만 돌려준다. */
@Service
@Transactional(readOnly = true)
public class ProjectEvidenceQuery {

    private final JdbcClient jdbc;

    public ProjectEvidenceQuery(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Reference(long sourceId, long projectId, boolean reregistering) {
    }

    public Map<Long, Reference> episodes(Long memberId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return query("""
                SELECT e.id AS source_id, p.id AS project_id,
                       (p.last_confirmed_at IS NOT NULL AND p.status <> 'CONFIRMED') AS reregistering
                FROM app.episode e JOIN app.project p ON p.id = e.project_id
                WHERE p.member_id = :member AND e.id IN (:ids)
                """, memberId, ids);
    }

    public Map<Long, Reference> projects(Long memberId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return query("""
                SELECT id AS source_id, id AS project_id,
                       (last_confirmed_at IS NOT NULL AND status <> 'CONFIRMED') AS reregistering
                FROM app.project WHERE member_id = :member AND id IN (:ids)
                """, memberId, ids);
    }

    private Map<Long, Reference> query(String sql, Long memberId, Collection<Long> ids) {
        return jdbc.sql(sql).param("member", memberId).param("ids", ids)
                .query(Reference.class).list().stream()
                .collect(Collectors.toUnmodifiableMap(Reference::sourceId, value -> value));
    }
}
