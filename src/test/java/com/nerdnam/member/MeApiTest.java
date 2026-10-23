package com.nerdnam.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 내 정보·온보딩 진행 상태·라이브러리(S06)·탈퇴 */
class MeApiTest extends ApiTestSupport {

    @Test
    void 내_정보() throws Exception {
        Long member = newMember();

        getJson(member, "/api/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member))
                .andExpect(jsonPath("$.displayName").value("테스트 회원"));
    }

    @Test
    void 온보딩_진행_상태는_다음에_갈_화면과_검토_중인_프로젝트를_알려준다() throws Exception {
        Long member = newMember();
        getJson(member, "/api/me/onboarding").andExpect(jsonPath("$.nextStep").value("skills"));

        Long redis = addSkill(member, "Redis");
        getJson(member, "/api/me/onboarding").andExpect(jsonPath("$.nextStep").value("career"));

        Long project = readLong(postJson(member, """
                {"title": "결제", "rawText": "응답이 1.2초였다. Redis 캐시를 붙였다. 0.3초가 됐다.", "userSkillIds": [%d]}
                """.formatted(redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project);
        getJson(member, "/api/me/onboarding")
                .andExpect(jsonPath("$.nextStep").value("project"))
                .andExpect(jsonPath("$.reviewingProjectId").value(project));

        Long episode = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, episode);
        postJson(member, null, "/api/projects/{id}/confirm", project);
        getJson(member, "/api/me/onboarding")
                .andExpect(jsonPath("$.nextStep").value("library"))
                .andExpect(jsonPath("$.reviewingProjectId").doesNotExist())
                .andExpect(jsonPath("$.stats.confirmedEpisodeCount").value(1));
    }

    @Test
    void 라이브러리는_통계_프로젝트_카드_경력_기술을_한_번에_준다() throws Exception {
        Long member = newMember();
        Long redis = addSkill(member, "Redis");
        Long project = readLong(postJson(member, """
                {"title": "결제 승인 API", "rawText": "응답이 1.2초였다. Redis 캐시를 붙였다. 0.3초가 됐다.", "userSkillIds": [%d]}
                """.formatted(redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project);
        Long episode = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, episode);
        postJson(member, null, "/api/projects/{id}/confirm", project);
        postJson(member, null, "/api/projects/{id}/reopen", project);

        getJson(member, "/api/me/library")
                .andExpect(jsonPath("$.stats.skillCount").value(1))
                .andExpect(jsonPath("$.stats.confirmedProjectCount").value(1))
                .andExpect(jsonPath("$.projects", hasSize(1)))
                .andExpect(jsonPath("$.projects[0].reregistering").value(true))
                .andExpect(jsonPath("$.projects[0].skills[0].name").value("Redis"))
                .andExpect(jsonPath("$.projects[0].confirmedEpisodes", hasSize(1)))
                .andExpect(jsonPath("$.projects[0].confirmedEpisodes[0].summaryLine", startsWith("응답이 1.2초였다. → ")))
                .andExpect(jsonPath("$.skills[0].level").value(1));
    }

    @Test
    void 탈퇴하면_원본과_벡터가_모두_지워진다() throws Exception {
        Long member = newMember();
        Long redis = addSkill(member, "Redis");
        Long project = readLong(postJson(member, """
                {"title": "결제", "rawText": "응답이 1.2초였다. Redis 캐시를 붙였다. 0.3초가 됐다.", "userSkillIds": [%d]}
                """.formatted(redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project);
        Long episode = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, episode);
        postJson(member, null, "/api/projects/{id}/confirm", project);
        Long posting = readLong(postJson(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Redis 경험"]}
                """, "/api/job-postings"), "$.id");
        postJson(member, null, "/api/job-postings/{id}/analyses", posting);
        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting);

        mvc.perform(delete("/api/me").header(MEMBER_HEADER, member)).andExpect(status().isNoContent());

        for (String table : List.of("app.member", "app.user_skill", "app.project", "app.job_posting", "app.fit_analysis",
                "app.ai_job", "app.ai_call_log", "rag.chunk")) {
            String column = table.equals("app.member") ? "id" : "member_id";
            Integer left = jdbc.sql("SELECT count(*) FROM " + table + " WHERE " + column + " = :m")
                    .param("m", member).query(Integer.class).single();
            assertThat(left).as(table).isZero();
        }
        getJson(member, "/api/me").andExpect(status().isUnauthorized());
    }
}
