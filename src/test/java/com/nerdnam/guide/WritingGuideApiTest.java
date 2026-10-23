package com.nerdnam.guide;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

/** 작성 방향 탭 생성·조회 (S09)와 만족도 평가 (S05·S09) */
class WritingGuideApiTest extends ApiTestSupport {

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void 프로젝트를_삭제해도_이전_리포트와_작성_방향에_인용_이름이_남는다(boolean legacy) throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);
        Long project = jdbc.sql("SELECT id FROM app.project WHERE member_id = :m")
                .param("m", member).query(Long.class).single();
        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isAccepted());
        String path = "$.citations[?(@.sourceType == 'episode')].label";
        java.util.List<String> labels = read(getJson(member, "/api/job-postings/{id}/guides/motive", posting), path);
        org.assertj.core.api.Assertions.assertThat(labels).isNotEmpty();
        if (legacy) {
            jdbc.sql("UPDATE app.writing_guide SET citation_snapshots = NULL WHERE fit_analysis_id IN "
                    + "(SELECT id FROM app.fit_analysis WHERE member_id = :m)").param("m", member).update();
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/{id}", project)
                .header(MEMBER_HEADER, member)).andExpect(status().isNoContent());
        getJson(member, "/api/job-postings/{id}/report", posting)
                .andExpect(jsonPath("$.matches[0].evidences[?(@.sourceType == 'episode')].deleted").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.matches[0].evidences[?(@.sourceType == 'episode')].snippet").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.matches[0].evidences[?(@.sourceType == 'episode')].projectId").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        getJson(member, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(jsonPath(path).value(labels))
                .andExpect(jsonPath("$.citations[?(@.sourceType == 'episode')].deleted").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.citations[?(@.sourceType == 'episode')].projectId").value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));
        postJson(member, "{\"feedback\":\"남은 경험만 사용해 주세요\"}", "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(status().isAccepted());
        getJson(member, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.citations[?(@.sourceType == 'episode')]").isEmpty());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void 리포트_직접_근거가_아닌_인접_경험도_삭제_후_인용_이름이_남는다(boolean legacy) throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);
        Long project = insertProject(member, "인접 경험", null);
        Long chunk = jdbc.sql("""
                INSERT INTO rag.chunk (member_id, chunk_type, source_id, source_version, project_id, content)
                VALUES (:m, 'PROJECT_SUMMARY', :p, 0, :p, '프로젝트: 인접 경험') RETURNING id
                """).param("m", member).param("p", project).query(Long.class).single();
        Long analysis = readLong(getJson(member, "/api/job-postings/{id}/report", posting), "$.analysisId");
        jdbc.sql("UPDATE app.requirement_match SET gap_cited_chunk_ids = jsonb_build_array(CAST(:c AS bigint)) "
                + "WHERE fit_analysis_id = :a AND gap_advice IS NOT NULL").param("c", chunk).param("a", analysis).update();
        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isAccepted());
        jdbc.sql("UPDATE app.writing_guide SET content = jsonb_set(content, '{cited_chunk_ids}', "
                + "jsonb_build_array(CAST(:c AS bigint))) WHERE fit_analysis_id = :a")
                .param("c", chunk).param("a", analysis).update();
        if (legacy) {
            jdbc.sql("UPDATE app.writing_guide SET citation_snapshots = NULL WHERE fit_analysis_id = :a")
                    .param("a", analysis).update();
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/projects/{id}", project)
                .header(MEMBER_HEADER, member)).andExpect(status().isNoContent());
        getJson(member, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(jsonPath("$.citations", hasSize(1)))
                .andExpect(jsonPath("$.citations[0].label").value("인접 경험"))
                .andExpect(jsonPath("$.citations[0].deleted").value(true))
                .andExpect(jsonPath("$.citations[0].projectId").doesNotExist());
    }

    @Test
    void 생성_중인_탭의_작업_id를_목록과_상세에서_돌려준다() throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);
        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isAccepted());
        Long analysis = readLong(getJson(member, "/api/job-postings/{id}/report", posting), "$.analysisId");
        Long running = jdbc.sql("""
                INSERT INTO app.ai_job (member_id, job_type, mode, target_type, target_id, lock_scope)
                VALUES (:m, 'WRITING_GUIDE', 'motive', 'FIT_ANALYSIS', :a, 'motive') RETURNING id
                """).param("m", member).param("a", analysis).query(Long.class).single();
        getJson(member, "/api/job-postings/{id}/guides", posting)
                .andExpect(jsonPath("$.tabs[0].runningJobId").value(running));
        getJson(member, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(jsonPath("$.runningJobId").value(running));
    }

    @Test
    void 탭을_만들고_다시_만들면_생성_번호가_오르고_인용_근거가_나온다() throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);

        getJson(member, "/api/job-postings/{id}/guides", posting)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabs", hasSize(5)))
                .andExpect(jsonPath("$.tabs[0].path").value("motive"))
                // 요청 경로와 응답이 같은 이름이다 (SPEC PR #47 FE 의견 1 — 전에는 tab 이 "motivation" 이었다)
                .andExpect(jsonPath("$.tabs[0].tab").value("motive"))
                .andExpect(jsonPath("$.tabs[1].tab").value("intro"))
                .andExpect(jsonPath("$.tabs[3].tab").value("career"))
                .andExpect(jsonPath("$.tabs[0].status").doesNotExist());
        getJson(member, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isNotFound());

        Long jobId = readLong(postJson(member, null, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(status().isAccepted()), "$.jobId");
        getJson(member, "/api/ai-jobs/{id}", jobId)
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.lockScope").value("motive"));   // 탭의 표준 이름 (웹 주소·AI 계약과 같은 값)

        getJson(member, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.tab").value("motive"))
                .andExpect(jsonPath("$.path").value("motive"))
                .andExpect(jsonPath("$.generationNo").value(1))
                .andExpect(jsonPath("$.content.core_message").value("OO페이 백엔드 개발자에 맞춘 핵심 메시지"))
                .andExpect(jsonPath("$.citations", not(empty())))
                .andExpect(jsonPath("$.citations[0].label").exists());

        // 다시 만들기는 피드백이 필수다 (C-02 S09 4 — 같은 조건으로 다시 만드는 요청을 막는다)
        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("어떤 방향으로 다시 만들지 알려 주세요."));
        postJson(member, "{\"feedback\":\"   \"}", "/api/job-postings/{id}/guides/motive", posting)
                .andExpect(status().isBadRequest());
        postJson(member, """
                {"feedback": "더 짧게"}
                """, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isAccepted());
        getJson(member, "/api/job-postings/{id}/guides/MOTIVATION", posting)
                .andExpect(jsonPath("$.generationNo").value(2))
                .andExpect(jsonPath("$.feedback").value("더 짧게"))
                .andExpect(jsonPath("$.content.feedback_applied").value("더 짧게"));
        getJson(member, "/api/job-postings/{id}/guides", posting)
                .andExpect(jsonPath("$.tabs[0].status").value("completed"))
                .andExpect(jsonPath("$.tabs[0].generationNo").value(2));
    }

    @Test
    void 포트폴리오_페이지는_순서대로_저장된다() throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);

        postJson(member, null, "/api/job-postings/{id}/guides/portfolio", posting).andExpect(status().isAccepted());

        getJson(member, "/api/job-postings/{id}/guides/portfolio", posting)
                .andExpect(jsonPath("$.content.pages[0].order").value(1))
                .andExpect(jsonPath("$.content.pages[0].kind").value("cover"));
    }

    @Test
    void 분석이_끝나지_않은_공고는_작성_방향을_만들_수_없다() throws Exception {
        Long member = newMember();
        Long posting = readLong(postJson(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Redis"]}
                """, "/api/job-postings"), "$.id");

        postJson(member, null, "/api/job-postings/{id}/guides/motive", posting).andExpect(status().isNotFound());
        postJson(member, null, "/api/job-postings/{id}/guides/unknown", posting).andExpect(status().isNotFound());
    }

    @Test
    void 만족도_평가는_대상마다_하나이고_다시_보내면_덮어쓴다() throws Exception {
        Long member = newMember();
        Long posting = analyzedPosting(member);
        Long other = newMember();

        getJson(member, "/api/job-postings/{id}/guides/rating", posting).andExpect(status().isNoContent());
        putJson(member, """
                {"score": 4, "comment": "도움이 됐어요"}
                """, "/api/job-postings/{id}/guides/rating", posting).andExpect(status().isOk());
        putJson(member, """
                {"score": 5}
                """, "/api/job-postings/{id}/guides/rating", posting)
                .andExpect(jsonPath("$.score").value(5))
                .andExpect(jsonPath("$.comment").doesNotExist());
        getJson(member, "/api/job-postings/{id}/guides/rating", posting).andExpect(jsonPath("$.score").value(5));

        putJson(member, """
                {"score": 6}
                """, "/api/job-postings/{id}/guides/rating", posting).andExpect(status().isBadRequest());
        putJson(other, """
                {"score": 3}
                """, "/api/job-postings/{id}/guides/rating", posting).andExpect(status().isNotFound());

        Long project = readLong(postJson(member, null, "/api/projects"), "$.id");
        putJson(member, """
                {"score": 2, "comment": "사례가 너무 잘게 나뉘었어요"}
                """, "/api/projects/{id}/rating", project)
                .andExpect(jsonPath("$.targetType").value("episode_generation"));
    }

    /** Redis 를 쓴 확정 프로젝트가 있는 회원의 분석 완료 공고 */
    private Long analyzedPosting(Long member) throws Exception {
        Long redis = addSkill(member, "Redis");
        Long project = readLong(postJson(member, """
                {"title": "결제 승인 API 성능 개선", "rawText": "응답이 1.2초였다. Redis 캐시를 붙였다. 0.3초가 됐다.",
                 "userSkillIds": [%d]}
                """.formatted(redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project);
        Long episode = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, episode);
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());
        Long posting = readLong(postJson(member, """
                {"companyName": "OO페이", "positionName": "백엔드 개발자", "required": ["Redis 캐시 설계 경험"],
                 "preferred": ["Kubernetes 운영 경험"], "tasks": ["결제 승인"]}
                """, "/api/job-postings"), "$.id");
        postJson(member, null, "/api/job-postings/{id}/analyses", posting).andExpect(status().isAccepted());
        return posting;
    }
}
