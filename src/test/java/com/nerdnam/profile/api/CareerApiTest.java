package com.nerdnam.profile.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 내 경력 API (S03) */
class CareerApiTest extends ApiTestSupport {

    private static final String CAREER = """
            {"companyName": "OO커머스", "teamName": "결제플랫폼팀", "jobTitle": "백엔드 개발자", "positionTitle": "선임",
             "employmentType": "FULL_TIME", "startMonth": "%s", "endMonth": %s,
             "duties": ["결제 API 설계·운영", "  ", ""]}
            """;

    @Test
    void 미래_월은_경력_생성과_수정에서_400이다() throws Exception {
        Long member = newMember();
        String next = java.time.YearMonth.now(java.time.ZoneId.of("Asia/Seoul")).plusMonths(1).toString();
        create(member, CAREER.formatted(next, "null")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이번 달까지만 입력할 수 있어요."));
        Long career = idOf(create(member, CAREER.formatted("2023-03", "null")));
        String updated = CAREER.formatted("2023-03", "\"" + next + "\"").strip();
        updated = updated.substring(0, updated.length() - 1) + ", \"version\": 0}";
        putJson(member, updated, "/api/me/careers/{id}", career).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이번 달까지만 입력할 수 있어요."));
    }

    @Test
    void 경력을_추가하면_빈_업무_줄은_버리고_재직_중을_표시한다() throws Exception {
        Long member = newMember();

        create(member, CAREER.formatted("2023-03", "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("OO커머스"))
                .andExpect(jsonPath("$.startMonth").value("2023-03"))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.duties", hasSize(1)))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void 목록은_입사_월이_늦은_것부터다() throws Exception {
        Long member = newMember();
        create(member, CAREER.formatted("2019-01", "\"2022-12\""));
        create(member, CAREER.formatted("2023-03", "null"));

        mvc.perform(get("/api/me/careers").header(MEMBER_HEADER, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].startMonth").value("2023-03"))
                .andExpect(jsonPath("$[1].endMonth").value("2022-12"));
    }

    @Test
    void 받은_version_으로_수정하고_예전_version_이면_409다() throws Exception {
        Long member = newMember();
        Long id = idOf(create(member, CAREER.formatted("2023-03", "null")));
        String updated = """
                {"companyName": "OO커머스", "teamName": "정산팀", "jobTitle": "백엔드 개발자",
                 "employmentType": "FULL_TIME", "startMonth": "2023-03", "endMonth": "2025-06",
                 "duties": ["정산 배치 쿼리 개선"], "version": %d}
                """;

        mvc.perform(put("/api/me/careers/{id}", id).header(MEMBER_HEADER, member)
                        .contentType(MediaType.APPLICATION_JSON).content(updated.formatted(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamName").value("정산팀"))
                .andExpect(jsonPath("$.current").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/me/careers/{id}", id).header(MEMBER_HEADER, member)
                        .contentType(MediaType.APPLICATION_JSON).content(updated.formatted(0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("CONFLICT"));
    }

    @Test
    void 입력이_잘못되면_400과_이유를_준다() throws Exception {
        Long member = newMember();

        create(member, """
                {"teamName": "팀", "jobTitle": "직무", "employmentType": "FULL_TIME", "startMonth": "2023-03"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("companyName")));

        create(member, CAREER.formatted("2023-03", "\"2023-01\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("퇴사 월이 입사 월보다 빨라요."));
    }

    @Test
    void 기술스택에_없는_기술은_연결할_수_없다() throws Exception {
        Long member = newMember();

        create(member, """
                {"companyName": "회사", "teamName": "팀", "jobTitle": "직무", "employmentType": "FULL_TIME",
                 "startMonth": "2023-03", "userSkillIds": [999999]}
                """)
                .andExpect(status().isBadRequest());
    }

    @Test
    void 프로젝트가_연결된_경력은_지울_수_없고_그_프로젝트를_알려준다() throws Exception {
        Long member = newMember();
        Long careerId = idOf(create(member, CAREER.formatted("2023-03", "null")));
        Long projectId = insertProject(member, "결제 승인 API 성능 개선", careerId);

        mvc.perform(delete("/api/me/careers/{id}", careerId).header(MEMBER_HEADER, member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.usages[0].type").value("project"))
                .andExpect(jsonPath("$.usages[0].id").value(projectId));
    }

    @Test
    void 연결된_것이_없으면_지워지고_남의_경력은_지울_수_없다() throws Exception {
        Long member = newMember();
        Long other = newMember();
        Long careerId = idOf(create(member, CAREER.formatted("2023-03", "null")));

        mvc.perform(delete("/api/me/careers/{id}", careerId).header(MEMBER_HEADER, other))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/careers/{id}", careerId).header(MEMBER_HEADER, member))
                .andExpect(status().isNoContent());
    }

    private ResultActions create(Long member, String body) throws Exception {
        return mvc.perform(post("/api/me/careers").header(MEMBER_HEADER, member)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static Long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
