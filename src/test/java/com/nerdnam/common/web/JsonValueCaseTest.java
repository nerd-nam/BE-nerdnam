package com.nerdnam.common.web;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

/**
 * 응답 JSON 의 값 표기 (D-10, 기획서 v1.25). 한 번 정하면 웹이 그 값으로 분기하므로 테스트로 고정한다.
 *
 * <ul>
 *   <li>enum 값은 <b>소문자</b>: 판정·상태·유형 등 (`spring.jackson.datatype.enum.write_enums_to_lowercase`)</li>
 *   <li>여러 낱말은 밑줄: {@code IN_PROGRESS} → {@code in_progress}</li>
 *   <li>요청은 대·소문자 모두 받는다 (`accept-case-insensitive-enums`) — 웹이 바뀌는 중에도 깨지지 않게</li>
 *   <li>예외: 오류 코드({@code ProblemDetail.title})는 <b>대문자 그대로</b>. 값이 아니라 코드다</li>
 * </ul>
 */
class JsonValueCaseTest extends ApiTestSupport {

    @Test
    void 응답의_enum_값은_소문자다() throws Exception {
        Long member = newMember();

        postJson(member, """
                {"title": "값 표기 확인", "affiliation": "PERSONAL", "rawText": "원문"}
                """, "/api/projects")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.affiliation").value("personal"));
    }

    @Test
    void 여러_낱말인_값은_밑줄로_잇는다() throws Exception {
        Long member = newMember();
        Long project = readLong(postJson(member, """
                {"title": "값 표기 확인", "affiliation": "PERSONAL", "rawText": "응답이 1.2초까지 느려졌다. 캐시로 0.3초가 됐다."}
                """, "/api/projects"), "$.id");
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());

        getJson(member, "/api/projects/{id}/review", project)
                .andExpect(jsonPath("$.episodes[0].versions[0].changeType").value("ai_split"))
                .andExpect(jsonPath("$.episodes[0].origin").value("ai"));
    }

    @Test
    void 요청은_대문자도_소문자도_받는다() throws Exception {
        Long member = newMember();

        postJson(member, """
                {"title": "소문자로 보낸 요청", "affiliation": "personal", "rawText": "원문"}
                """, "/api/projects")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.affiliation").value("personal"));
    }

    @Test
    void 오류_코드는_대문자_그대로다() throws Exception {
        Long member = newMember();

        getJson(member, "/api/projects/{id}", 999_999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("프로젝트를 찾을 수 없어요."));
    }
}
