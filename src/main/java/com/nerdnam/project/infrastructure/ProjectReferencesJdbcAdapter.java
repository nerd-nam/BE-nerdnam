package com.nerdnam.project.infrastructure;

import com.nerdnam.profile.application.ProjectReferences;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * profile 의 질문(이 기술·경력을 쓰는 프로젝트가 있나)에 project 모듈이 답한다.
 * Project 애그리거트를 아직 만들기 전이라 SQL 로 읽는다. 묶음 1에서 Project 저장소가 생기면 그쪽 조회로 바꿔도 된다.
 */
@Component
class ProjectReferencesJdbcAdapter implements ProjectReferences {

    private final JdbcClient jdbc;

    ProjectReferencesJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProjectRef> projectsUsingUserSkill(Long memberId, Long userSkillId) {
        return jdbc.sql("""
                        SELECT p.id, p.title
                        FROM app.project p
                        JOIN app.project_skill ps ON ps.project_id = p.id
                        WHERE p.member_id = :memberId AND ps.user_skill_id = :userSkillId
                        ORDER BY p.id
                        """)
                .param("memberId", memberId)
                .param("userSkillId", userSkillId)
                .query(ProjectRef.class)
                .list();
    }

    @Override
    public List<ProjectRef> projectsOfCareer(Long memberId, Long careerId) {
        return jdbc.sql("""
                        SELECT id, title FROM app.project
                        WHERE member_id = :memberId AND career_id = :careerId
                        ORDER BY id
                        """)
                .param("memberId", memberId)
                .param("careerId", careerId)
                .query(ProjectRef.class)
                .list();
    }
}
