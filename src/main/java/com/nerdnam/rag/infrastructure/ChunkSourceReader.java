package com.nerdnam.rag.infrastructure;

import com.nerdnam.rag.application.ChunkSources.CareerSource;
import com.nerdnam.rag.application.ChunkSources.EpisodeSource;
import com.nerdnam.rag.application.ChunkSources.ProjectSource;
import com.nerdnam.rag.application.ChunkSources.SkillSource;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 청크 재료를 원본 테이블(app 스키마)에서 SQL 로 읽는다.
 *
 * <p>rag 는 원본에서 파생되는 읽기 모델(프로젝션)이라 원본 모듈의 엔티티를 쓰지 않고 테이블을 직접 읽는다.
 * 쓰기는 하지 않는다. 원본 테이블 모양이 바뀌면 이 클래스도 함께 고친다.
 */
@Component
public class ChunkSourceReader {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public ChunkSourceReader(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 회원의 기술스택 + 사용처 (경력, 확정본이 있는 프로젝트) */
    public List<SkillSource> skills(long memberId) {
        Map<Long, List<String>> usages = new HashMap<>();
        jdbc.sql("""
                        SELECT cs.user_skill_id, c.company_name
                        FROM app.career_skill cs JOIN app.career c ON c.id = cs.career_id
                        WHERE c.member_id = :m
                        ORDER BY c.start_month DESC, c.id
                        """)
                .param("m", memberId)
                .query((rs, n) -> usages.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(rs.getString(2) + " 경력"))
                .list();
        jdbc.sql("""
                        SELECT ps.user_skill_id, p.title
                        FROM app.project_skill ps JOIN app.project p ON p.id = ps.project_id
                        WHERE p.member_id = :m AND p.last_confirmed_at IS NOT NULL
                        ORDER BY p.id
                        """)
                .param("m", memberId)
                .query((rs, n) -> usages.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
                        .add(rs.getString(2) + " 프로젝트"))
                .list();
        return jdbc.sql("""
                        SELECT us.id, s.name, s.category, us.level
                        FROM app.user_skill us JOIN app.skill s ON s.id = us.skill_id
                        WHERE us.member_id = :m
                        ORDER BY us.id
                        """)
                .param("m", memberId)
                .query((rs, n) -> new SkillSource(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        List.copyOf(usages.getOrDefault(rs.getLong(1), List.of()))))
                .list();
    }

    public List<CareerSource> careers(long memberId) {
        Map<Long, List<String>> duties = new HashMap<>();
        jdbc.sql("""
                        SELECT d.career_id, d.content FROM app.career_duty d JOIN app.career c ON c.id = d.career_id
                        WHERE c.member_id = :m ORDER BY d.career_id, d.line_no
                        """)
                .param("m", memberId)
                .query((rs, n) -> duties.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)))
                .list();
        Map<Long, List<String>> skills = new HashMap<>();
        jdbc.sql("""
                        SELECT cs.career_id, s.name
                        FROM app.career_skill cs
                        JOIN app.career c ON c.id = cs.career_id
                        JOIN app.user_skill us ON us.id = cs.user_skill_id
                        JOIN app.skill s ON s.id = us.skill_id
                        WHERE c.member_id = :m ORDER BY cs.career_id, s.name
                        """)
                .param("m", memberId)
                .query((rs, n) -> skills.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>()).add(rs.getString(2)))
                .list();
        return jdbc.sql("""
                        SELECT id, version, company_name, team_name, job_title, position_title, employment_type,
                               start_month, end_month
                        FROM app.career WHERE member_id = :m ORDER BY start_month DESC, id
                        """)
                .param("m", memberId)
                .query((rs, n) -> new CareerSource(rs.getLong("id"), rs.getInt("version"), rs.getString("company_name"),
                        rs.getString("team_name"), rs.getString("job_title"), rs.getString("position_title"),
                        rs.getString("employment_type"), month(rs.getDate("start_month")), month(rs.getDate("end_month")),
                        List.copyOf(duties.getOrDefault(rs.getLong("id"), List.of())),
                        List.copyOf(skills.getOrDefault(rs.getLong("id"), List.of()))))
                .list();
    }

    /** 확정된 프로젝트만. 확정 상태가 아니면(재등록 중 포함) 비어 있다 — 그때는 기존 청크를 그대로 둔다 (Q4) */
    public Optional<ProjectSource> confirmedProject(long memberId, long projectId) {
        List<String> skills = jdbc.sql("""
                        SELECT s.name FROM app.project_skill ps
                        JOIN app.user_skill us ON us.id = ps.user_skill_id
                        JOIN app.skill s ON s.id = us.skill_id
                        WHERE ps.project_id = :p ORDER BY s.name
                        """)
                .param("p", projectId)
                .query(String.class)
                .list();
        List<EpisodeSource> episodes = jdbc.sql("""
                        SELECT e.id, v.version_no, v.title, v.role, v.problem, v.solution, v.result,
                               v.skills::text AS skills, jsonb_array_length(v.metrics) > 0 AS has_metric
                        FROM app.episode e
                        JOIN app.episode_version v ON v.episode_id = e.id AND v.version_no = e.current_version
                        WHERE e.project_id = :p AND e.status = 'CONFIRMED'
                        ORDER BY e.display_order, e.id
                        """)
                .param("p", projectId)
                .query((rs, n) -> new EpisodeSource(rs.getLong("id"), rs.getInt("version_no"), rs.getString("title"),
                        rs.getString("role"), rs.getString("problem"), rs.getString("solution"), rs.getString("result"),
                        JSON.readValue(rs.getString("skills"), STRINGS), rs.getBoolean("has_metric")))
                .list();
        return jdbc.sql("""
                        SELECT id, version, title, period_start, period_end FROM app.project
                        WHERE id = :p AND member_id = :m AND status = 'CONFIRMED'
                        """)
                .param("p", projectId)
                .param("m", memberId)
                .query((rs, n) -> new ProjectSource(rs.getLong("id"), rs.getInt("version"), rs.getString("title"),
                        month(rs.getDate("period_start")), month(rs.getDate("period_end")), skills, episodes))
                .optional();
    }

    private static YearMonth month(Date date) {
        return date == null ? null : YearMonth.from(date.toLocalDate());
    }
}
