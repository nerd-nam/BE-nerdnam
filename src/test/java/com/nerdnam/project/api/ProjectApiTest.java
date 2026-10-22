package com.nerdnam.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 프로젝트 입력·재구성 검토·재등록 API (S04·S05·S06). AI 는 스텁이다:
 * 빈 줄로 나뉜 문단 하나가 사례 하나가 되고, 숫자가 없는 문단에는 "result-metric" 확인 질문이 달린다.
 */
class ProjectApiTest extends ApiTestSupport {

    /** 문단 1: 숫자 있음 → 질문 없음 / 문단 2: 숫자 없음 → 확인 질문 */
    private static final String RAW_TEXT = """
            결제 승인 응답이 1.2초까지 느려졌다. Spring Boot 서버에 Redis 캐시를 붙였다. 평균 응답이 0.3초가 됐다.

            프로모션 전에 부하 테스트 절차가 없었다. 실제 트래픽 패턴으로 테스트 절차를 만들었다. 다음 프로모션을 문제없이 넘겼다.""";

    @Test
    void 임시_저장부터_나누기_답변_결정_확정까지() throws Exception {
        Long member = newMember();
        Long project = createProject(member, RAW_TEXT);

        Long jobId = readLong(postJson(member, null, "/api/projects/{id}/split", project)
                .andExpect(status().isAccepted()), "$.jobId");

        getJson(member, "/api/ai-jobs/{id}", jobId)
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.jobType").value("RESTRUCTURE"))
                .andExpect(jsonPath("$.mode").value("SPLIT"))
                .andExpect(jsonPath("$.resultId").value(project));

        ResultActions review = getJson(member, "/api/projects/{id}/review", project)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"))
                .andExpect(jsonPath("$.splitRound").value(1))
                .andExpect(jsonPath("$.episodes", hasSize(2)))
                .andExpect(jsonPath("$.episodes[0].skills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.episodes[0].sourceSpans[0][0]").value(0))
                .andExpect(jsonPath("$.episodes[0].versions[0].changeType").value("AI_SPLIT"))
                .andExpect(jsonPath("$.episodes[1].clarifications[0].key").value("result-metric"))
                .andExpect(jsonPath("$.runningJobId").doesNotExist());
        Long first = readLong(review, "$.episodes[0].id");
        Long second = readLong(review, "$.episodes[1].id");

        // 확인 필요가 남은 사례는 확정할 수 없다 (P2)
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, second)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("확인 필요")));

        // 답하면 AI 가 새 버전을 만들고 답변은 원문 뒤에 붙는다
        postJson(member, """
                {"answer": "장애 0건으로 넘겼다"}
                """, "/api/projects/{id}/episodes/{e}/clarifications/{key}/answer", project, second, "result-metric")
                .andExpect(status().isAccepted());
        getJson(member, "/api/projects/{id}/review", project)
                .andExpect(jsonPath("$.episodes[1].currentVersion").value(2))
                .andExpect(jsonPath("$.episodes[1].clarifications", hasSize(0)))
                .andExpect(jsonPath("$.episodes[1].result", containsString("장애 0건")))
                .andExpect(jsonPath("$.supplements", hasSize(1)))
                .andExpect(jsonPath("$.sourceText", containsString("답변: 장애 0건으로 넘겼다")))
                .andExpect(jsonPath("$.generationCount").value(2));

        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, first).andExpect(status().isOk());
        postJson(member, null, "/api/projects/{id}/confirm", project)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("모든 사례를")));
        postJson(member, null, "/api/projects/{id}/episodes/{e}/exclude", project, second).andExpect(status().isOk());

        postJson(member, null, "/api/projects/{id}/confirm", project)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.episodes[*].status", contains("CONFIRMED", "EXCLUDED")));

        Integer calls = jdbc.sql("""
                        SELECT count(*) FROM app.ai_call_log
                        WHERE member_id = :m AND operation = 'RESTRUCTURE' AND success AND target_id = :p
                        """)
                .param("m", member).param("p", project).query(Integer.class).single();
        assertThat(calls).isEqualTo(2);
    }

    @Test
    void 다시_나누면_결정이_초기화되고_이전_사례는_기록으로_남는다() throws Exception {
        Long member = newMember();
        Long project = splitProject(member);
        Long first = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, first);

        postJson(member, """
                {"feedback": " "}
                """, "/api/projects/{id}/resplit", project)
                .andExpect(status().isBadRequest());
        postJson(member, """
                {"feedback": "두 사례를 합쳐 줘"}
                """, "/api/projects/{id}/resplit", project)
                .andExpect(status().isAccepted());

        getJson(member, "/api/projects/{id}/review", project)
                .andExpect(jsonPath("$.splitRound").value(2))
                .andExpect(jsonPath("$.episodes", hasSize(1)))
                .andExpect(jsonPath("$.episodes[0].status").value("REVIEW"))
                .andExpect(jsonPath("$.episodes[0].versions[0].feedback").value("두 사례를 합쳐 줘"));
        assertThat(episodeStatuses(project)).containsExactlyInAnyOrder("SUPERSEDED", "SUPERSEDED", "REVIEW");
    }

    @Test
    void 직접_편집_빼고_진행_이_버전으로_바꾸기_사례_추가() throws Exception {
        Long member = newMember();
        Long project = splitProject(member);
        Long second = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[1].id");

        postJson(member, null, "/api/projects/{id}/episodes/{e}/clarifications/{key}/drop", project, second, "result-metric")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[1].clarifications", hasSize(0)))
                .andExpect(jsonPath("$.episodes[1].versions[1].changeType").value("USER_EDIT"));

        putJson(member, """
                {"title": "부하 테스트 절차 도입", "role": "절차 설계", "problem": "문제", "solution": "해결", "result": "결과"}
                """, "/api/projects/{id}/episodes/{e}", project, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[1].currentVersion").value(3))
                .andExpect(jsonPath("$.episodes[1].title").value("부하 테스트 절차 도입"));

        postJson(member, """
                {"versionNo": 1}
                """, "/api/projects/{id}/episodes/{e}/restore", project, second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.episodes[1].currentVersion").value(4))
                .andExpect(jsonPath("$.episodes[1].versions[3].changeType").value("RESTORED"))
                .andExpect(jsonPath("$.episodes[1].versions[3].restoredFrom").value(1))
                .andExpect(jsonPath("$.episodes[1].clarifications", hasSize(1)));

        postJson(member, """
                {"title": "AI 가 놓친 사례", "problem": "p", "solution": "s", "result": "r"}
                """, "/api/projects/{id}/episodes", project)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.episodes", hasSize(3)))
                .andExpect(jsonPath("$.episodes[2].origin").value("USER"))
                .andExpect(jsonPath("$.episodes[2].versions[0].changeType").value("USER_CREATED"));
    }

    @Test
    void 검토_결과를_버리면_초안으로_돌아가고_원문을_고칠_수_있다() throws Exception {
        Long member = newMember();
        Long project = splitProject(member);

        long reviewingVersion = readLong(getJson(member, "/api/projects/{id}", project), "$.version");
        putJson(member, draftBody("고친 원문", reviewingVersion), "/api/projects/{id}", project)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", containsString("검토 중인 결과를 정리한 뒤")));

        ResultActions discarded = postJson(member, null, "/api/projects/{id}/review/discard", project)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        long version = readLong(discarded, "$.version");

        putJson(member, draftBody("고친 원문", version), "/api/projects/{id}", project)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawText").value("고친 원문"));
        assertThat(episodeStatuses(project)).containsOnly("DISCARDED");
    }

    @Test
    void 재등록하면_이전_확정본을_유지한_채_다시_나누고_다시_확정하면_대체된다() throws Exception {
        Long member = newMember();
        Long project = confirmedProject(member);

        postJson(member, null, "/api/projects/{id}/reopen", project)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reregistering").value(true))
                .andExpect(jsonPath("$.rawText").value(RAW_TEXT));
        getJson(member, "/api/projects", project)
                .andExpect(jsonPath("$[0].reregistering").value(true))
                .andExpect(jsonPath("$[0].confirmedEpisodeCount").value(1));

        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
        ResultActions review = getJson(member, "/api/projects/{id}/review", project)
                .andExpect(jsonPath("$.splitRound").value(2))
                .andExpect(jsonPath("$.episodes", hasSize(2)));
        Long fresh = readLong(review, "$.episodes[0].id");
        Long freshSecond = readLong(review, "$.episodes[1].id");
        assertThat(episodeStatuses(project)).containsExactlyInAnyOrder("CONFIRMED", "EXCLUDED", "REVIEW", "REVIEW");

        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, fresh);
        postJson(member, null, "/api/projects/{id}/episodes/{e}/exclude", project, freshSecond);
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());

        assertThat(episodeStatuses(project))
                .containsExactlyInAnyOrder("SUPERSEDED", "SUPERSEDED", "CONFIRMED", "EXCLUDED");
    }

    @Test
    void 같은_프로젝트에_진행_중인_AI_작업이_있으면_409와_작업_id를_준다() throws Exception {
        Long member = newMember();
        Long project = createProject(member, RAW_TEXT);
        Long running = jdbc.sql("""
                        INSERT INTO app.ai_job (member_id, job_type, mode, target_type, target_id)
                        VALUES (:m, 'RESTRUCTURE', 'SPLIT', 'PROJECT', :p) RETURNING id
                        """)
                .param("m", member).param("p", project).query(Long.class).single();

        postJson(member, null, "/api/projects/{id}/split", project)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("AI_JOB_RUNNING"))
                .andExpect(jsonPath("$.runningJobId").value(running));
        getJson(member, "/api/projects/{id}", project).andExpect(jsonPath("$.runningJobId").value(running));
    }

    @Test
    void AI_결과를_반영하지_못하면_작업은_실패하고_프로젝트는_그대로다() throws Exception {
        Long member = newMember();
        Long project = createProject(member, "질문: 보충 답변처럼 보이는 문단만 있어 스텁이 사례를 만들지 못한다");

        Long jobId = readLong(postJson(member, null, "/api/projects/{id}/split", project), "$.jobId");

        getJson(member, "/api/ai-jobs/{id}", jobId)
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("AI_ERROR"));
        getJson(member, "/api/projects/{id}", project).andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void 남의_프로젝트와_작업은_볼_수_없다() throws Exception {
        Long owner = newMember();
        Long other = newMember();
        Long project = splitProject(owner);
        Long jobId = jdbc.sql("SELECT max(id) FROM app.ai_job WHERE target_id = :p").param("p", project)
                .query(Long.class).single();

        getJson(other, "/api/projects/{id}", project).andExpect(status().isNotFound());
        getJson(other, "/api/projects/{id}/review", project).andExpect(status().isNotFound());
        postJson(other, null, "/api/projects/{id}/split", project).andExpect(status().isNotFound());
        getJson(other, "/api/ai-jobs/{id}", jobId).andExpect(status().isNotFound());
    }

    @Test
    void 남의_기술이나_경력은_연결할_수_없다() throws Exception {
        Long owner = newMember();
        Long other = newMember();
        Long othersSkill = addSkill(other, "Kafka");

        postJson(owner, """
                {"title": "t", "userSkillIds": [%d]}
                """.formatted(othersSkill), "/api/projects")
                .andExpect(status().isBadRequest());
        postJson(owner, """
                {"title": "t", "affiliation": "CAREER"}
                """, "/api/projects")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("연결할 경력을 선택해 주세요."));
    }

    @Test
    void 임시_저장은_version이_다르면_409() throws Exception {
        Long member = newMember();
        Long project = createProject(member, "본문");

        putJson(member, draftBody("첫 저장", 0), "/api/projects/{id}", project).andExpect(status().isOk());
        putJson(member, draftBody("오래된 화면에서 저장", 0), "/api/projects/{id}", project)
                .andExpect(status().isConflict());
    }

    @Test
    void 프로젝트_입력에서_새_기술을_추가하면_기술스택에_학습_중으로_생기고_연결된다() throws Exception {
        Long member = newMember();
        Long project = createProject(member, "본문");
        String name = unique("NewTool");

        Long userSkillId = readLong(postJson(member, """
                {"name": "%s", "category": "TOOL"}
                """.formatted(name), "/api/projects/{id}/skills", project)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.proficiency").value("LEARNING")), "$.id");

        getJson(member, "/api/projects/{id}", project)
                .andExpect(jsonPath("$.skills[?(@.name == '%s')].userSkillId".formatted(name)).value(userSkillId.intValue()));
    }

    // ------------------------------------------------------------------ 준비

    private Long createProject(Long member, String rawText) throws Exception {
        Long springBoot = addSkill(member, "Spring Boot");
        Long redis = addSkill(member, "Redis");
        return readLong(postJson(member, """
                {"title": "결제 승인 API 성능 개선", "periodStart": "2024-05", "periodEnd": "2024-09",
                 "affiliation": "PERSONAL", "rawText": %s, "userSkillIds": [%d, %d],
                 "links": ["https://github.com/me/pay", " "]}
                """.formatted(quote(rawText), springBoot, redis), "/api/projects")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.links", hasSize(1)))
                .andExpect(jsonPath("$.periodStart").value("2024-05"))
                .andExpect(jsonPath("$.version").value(0)), "$.id");
    }

    private Long splitProject(Long member) throws Exception {
        Long project = createProject(member, RAW_TEXT);
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
        return project;
    }

    private Long confirmedProject(Long member) throws Exception {
        Long project = splitProject(member);
        ResultActions review = getJson(member, "/api/projects/{id}/review", project);
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, readLong(review, "$.episodes[0].id"));
        postJson(member, null, "/api/projects/{id}/episodes/{e}/exclude", project, readLong(review, "$.episodes[1].id"));
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());
        return project;
    }

    private List<String> episodeStatuses(Long project) {
        return jdbc.sql("SELECT status FROM app.episode WHERE project_id = :p ORDER BY id")
                .param("p", project).query(String.class).list();
    }

    private static String draftBody(String rawText, long version) {
        return """
                {"title": "결제 승인 API 성능 개선", "affiliation": "PERSONAL", "rawText": %s, "version": %d}
                """.formatted(quote(rawText), version);
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
