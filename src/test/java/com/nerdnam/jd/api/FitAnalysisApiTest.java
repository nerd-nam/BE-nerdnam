package com.nerdnam.jd.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 공고 입력 → 적합도 분석 → 리포트 (S07·S08·S10). AI 는 스텁이다:
 * 요건에서 영문 기술명·"N년"을 뽑고, 후보 청크에 그 기술명이 있으면 충족, 기술명이 없는 요건은 후보가 있으면 부분 충족.
 */
class FitAnalysisApiTest extends ApiTestSupport {

    @Test
    void 분석하면_요건별_판정_근거_갭_가이드_한_줄_진단이_나온다() throws Exception {
        Long member = memberWithExperience(36);
        Long posting = createPosting(member, """
                {"companyName": "OO페이", "positionName": "백엔드 개발자",
                 "required": ["Spring Boot 기반 개발 3년 이상", "Redis 캐시 설계 경험"],
                 "preferred": ["Kubernetes 운영 경험", "대용량 트래픽 처리 경험"],
                 "tasks": ["결제 승인 시스템 개발"], "companyIntro": "결제 회사"}
                """);

        Long jobId = readLong(postJson(member, null, "/api/job-postings/{id}/analyses", posting)
                .andExpect(status().isAccepted()), "$.jobId");
        Long analysisId = readLong(getJson(member, "/api/ai-jobs/{id}", jobId)
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.jobType").value("fit_analysis")), "$.resultId");

        getJson(member, "/api/job-postings/{id}/report", posting)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.companyName").value("OO페이"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.required.met").value(2))
                .andExpect(jsonPath("$.required.total").value(2))
                .andExpect(jsonPath("$.preferred.partial").value(1))
                .andExpect(jsonPath("$.preferred.unmet").value(1))
                .andExpect(jsonPath("$.diagnosis", startsWith("필수 2개 모두 근거 경험이 있어요.")))
                .andExpect(jsonPath("$.matches", hasSize(4)))
                // 1: Spring Boot 3년 — 경력 36개월이라 규칙도 충족
                .andExpect(jsonPath("$.matches[0].skills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.matches[0].minYears").value(3))
                .andExpect(jsonPath("$.matches[0].ruleVerdict").value("met"))
                .andExpect(jsonPath("$.matches[0].verdict").value("met"))
                .andExpect(jsonPath("$.matches[0].reason", containsString("관련 경력 3년 / 요구 3년")))
                .andExpect(jsonPath("$.matches[0].gapGuide").doesNotExist())
                .andExpect(jsonPath("$.matches[1].evidences[0].label").exists())
                // 3: Kubernetes — 근거 없음 → 미충족 + AI 갭 가이드
                .andExpect(jsonPath("$.matches[2].type").value("preferred"))
                .andExpect(jsonPath("$.matches[2].verdict").value("unmet"))
                .andExpect(jsonPath("$.matches[2].evidences", hasSize(0)))
                .andExpect(jsonPath("$.matches[2].gapGuide.source").value("ai"))
                .andExpect(jsonPath("$.matches[2].gapGuide.avoid").exists())
                // 4: 기술명 없는 요건 → 부분 충족
                .andExpect(jsonPath("$.matches[3].verdict").value("partial"))
                .andExpect(jsonPath("$.matches[3].gapGuide.advice").exists());

        // 근거 청크는 모두 이 회원의 것 (5.4-6)
        Integer foreign = jdbc.sql("""
                        SELECT count(*) FROM app.match_evidence me
                        JOIN app.requirement_match rm ON rm.id = me.requirement_match_id
                        LEFT JOIN rag.chunk c ON c.id = me.chunk_id
                        WHERE rm.fit_analysis_id = :a AND (c.id IS NULL OR c.member_id <> :m)
                        """)
                .param("a", analysisId).param("m", member).query(Integer.class).single();
        assertThat(foreign).isZero();

        // 요건 추출 결과는 공고에도 남는다 (다음에 열 때 보인다)
        getJson(member, "/api/job-postings/{id}", posting)
                .andExpect(jsonPath("$.required[0].skills[0]").value("Spring Boot"))
                .andExpect(jsonPath("$.required[0].minYears").value(3));
    }

    @Test
    void 연차가_모자라면_LLM이_충족이라도_부분_충족이다_D5() throws Exception {
        Long member = memberWithExperience(24);
        Long posting = createPosting(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Spring Boot 개발 3년 이상"]}
                """);

        postJson(member, null, "/api/job-postings/{id}/analyses", posting).andExpect(status().isAccepted());

        getJson(member, "/api/job-postings/{id}/report", posting)
                .andExpect(jsonPath("$.matches[0].llmVerdict").value("met"))
                .andExpect(jsonPath("$.matches[0].ruleVerdict").value("partial"))
                .andExpect(jsonPath("$.matches[0].verdict").value("partial"))
                .andExpect(jsonPath("$.matches[0].reason", containsString("관련 경력 2년 / 요구 3년")))
                .andExpect(jsonPath("$.matches[0].gapGuide.source").value("ai"))
                .andExpect(jsonPath("$.diagnosis").value("필수 1개 중 1개는 부분 충족이에요."));
    }

    @Test
    void 입력_오류가_있으면_400과_줄별_위반_목록을_주고_작업을_만들지_않는다() throws Exception {
        Long member = newMember();
        Long posting = createPosting(member, """
                {"companyName": "", "positionName": "백엔드", "required": [], "preferred": ["%s"]}
                """.formatted("가".repeat(151)));

        postJson(member, null, "/api/job-postings/{id}/analyses", posting)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("고쳐야 할 항목이 3개 있어요. 빨간색으로 표시된 곳을 확인해 주세요."))
                .andExpect(jsonPath("$.violations[0].code").value("company_name_required"))
                .andExpect(jsonPath("$.violations[0].message").value("회사명을 입력해 주세요."))
                .andExpect(jsonPath("$.violations[2].field").value("preferred"))
                .andExpect(jsonPath("$.violations[2].index").value(0));

        Integer jobs = jdbc.sql("SELECT count(*) FROM app.ai_job WHERE member_id = :m").param("m", member)
                .query(Integer.class).single();
        assertThat(jobs).isZero();
        getJson(member, "/api/job-postings/{id}/report", posting).andExpect(status().isNotFound());
    }

    @Test
    void 공고_목록은_분석_상태와_충족_수를_계산해서_보여준다() throws Exception {
        Long member = memberWithExperience(36);
        Long draft = createPosting(member, """
                {"companyName": "작성 중 회사", "positionName": "백엔드"}
                """);
        Long analyzed = createPosting(member, """
                {"companyName": "분석한 회사", "positionName": "백엔드", "required": ["Redis 경험"]}
                """);
        postJson(member, null, "/api/job-postings/{id}/analyses", analyzed).andExpect(status().isAccepted());

        ResultActions list = getJson(member, "/api/job-postings").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        assertThat((Object) read(list, "$[?(@.id == %d)].status".formatted(draft))).asString().contains("DRAFT");
        assertThat((Object) read(list, "$[?(@.id == %d)].status".formatted(analyzed))).asString().contains("COMPLETED");
        assertThat((Object) read(list, "$[?(@.id == %d)].requiredMet".formatted(analyzed))).asString().contains("1");
    }

    @Test
    void 공고를_고쳐_다시_분석하면_분석이_새로_쌓이고_이전_결과는_그대로다() throws Exception {
        Long member = memberWithExperience(36);
        Long posting = createPosting(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Redis 경험"]}
                """);
        postJson(member, null, "/api/job-postings/{id}/analyses", posting);
        Long first = readLong(getJson(member, "/api/job-postings/{id}/report", posting), "$.analysisId");

        putJson(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Kafka 운영 경험"], "version": %d}
                """.formatted(readLong(getJson(member, "/api/job-postings/{id}", posting), "$.version")),
                "/api/job-postings/{id}", posting).andExpect(status().isOk());
        postJson(member, null, "/api/job-postings/{id}/analyses", posting);

        getJson(member, "/api/job-postings/{id}/report", posting)
                .andExpect(jsonPath("$.analysisId").value(org.hamcrest.Matchers.not(first.intValue())))
                .andExpect(jsonPath("$.matches[0].text").value("Kafka 운영 경험"));
        getJson(member, "/api/fit-analyses/{id}", first)
                .andExpect(jsonPath("$.matches[0].text").value("Redis 경험"));

        // 분석 기록: 최신순으로 쌓인다 (S08 "이전 분석 보기", 기획서 v1.25)
        getJson(member, "/api/job-postings/{id}/analyses", posting)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("completed"))
                .andExpect(jsonPath("$[1].analysisId").value(first))
                .andExpect(jsonPath("$[0].requestedAt").exists());
    }

    @Test
    void 사전에_없는_요건_기술명은_원문_그대로_두고_기록한다() throws Exception {
        Long member = newMember();
        String unknown = unique("Zfw");
        Long posting = createPosting(member, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["%s 경험", "springboot 경험"]}
                """.formatted(unknown));

        postJson(member, null, "/api/job-postings/{id}/analyses", posting).andExpect(status().isAccepted());

        getJson(member, "/api/job-postings/{id}", posting)
                .andExpect(jsonPath("$.required[0].skills[0]").value(unknown))
                .andExpect(jsonPath("$.required[1].skills[0]").value("Spring Boot"));
        Integer logged = jdbc.sql("SELECT count(*) FROM app.unregistered_skill_name WHERE raw_name = :n AND source = 'JOB_POSTING'")
                .param("n", unknown).query(Integer.class).single();
        assertThat(logged).isEqualTo(1);
    }

    @Test
    void 남의_공고와_분석은_볼_수_없다() throws Exception {
        Long owner = memberWithExperience(36);
        Long other = newMember();
        Long posting = createPosting(owner, """
                {"companyName": "회사", "positionName": "백엔드", "required": ["Redis 경험"]}
                """);
        postJson(owner, null, "/api/job-postings/{id}/analyses", posting);
        Long analysisId = readLong(getJson(owner, "/api/job-postings/{id}/report", posting), "$.analysisId");

        getJson(other, "/api/job-postings/{id}", posting).andExpect(status().isNotFound());
        getJson(other, "/api/job-postings/{id}/report", posting).andExpect(status().isNotFound());
        getJson(other, "/api/fit-analyses/{id}", analysisId).andExpect(status().isNotFound());
        postJson(other, null, "/api/job-postings/{id}/analyses", posting).andExpect(status().isNotFound());
        getJson(other, "/api/job-postings").andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------ 준비

    /** Spring Boot·Redis 를 쓴 경력(개월 수 지정) + Redis 를 쓴 확정 프로젝트 */
    private Long memberWithExperience(int careerMonths) throws Exception {
        Long member = newMember();
        Long springBoot = addSkill(member, "Spring Boot");
        Long redis = addSkill(member, "Redis");
        java.time.YearMonth start = java.time.YearMonth.of(2018, 1);
        postJson(member, """
                {"companyName": "OO커머스", "teamName": "결제팀", "jobTitle": "백엔드", "employmentType": "FULL_TIME",
                 "startMonth": "%s", "endMonth": "%s", "duties": ["결제 API 운영"], "userSkillIds": [%d, %d]}
                """.formatted(start, start.plusMonths(careerMonths - 1), springBoot, redis), "/api/me/careers")
                .andExpect(status().isCreated());
        Long project = readLong(postJson(member, """
                {"title": "결제 승인 API 성능 개선", "rawText": "결제 승인 응답이 1.2초였다. Redis 캐시를 붙였다. 0.3초가 됐다.",
                 "userSkillIds": [%d]}
                """.formatted(redis), "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
        Long episode = readLong(getJson(member, "/api/projects/{id}/review", project), "$.episodes[0].id");
        postJson(member, null, "/api/projects/{id}/episodes/{e}/confirm", project, episode).andExpect(status().isOk());
        postJson(member, null, "/api/projects/{id}/confirm", project).andExpect(status().isOk());
        return member;
    }

    private Long createPosting(Long member, String body) throws Exception {
        return readLong(postJson(member, body, "/api/job-postings").andExpect(status().isCreated()), "$.id");
    }
}
