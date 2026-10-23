package com.nerdnam.profile.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 내 기술스택 API (S02) */
class UserSkillApiTest extends ApiTestSupport {

    @Test
    void 별칭으로_추가하면_표준_이름으로_저장되고_기본_숙련도는_학습_중이다() throws Exception {
        Long member = newMember();

        add(member, """
                {"name": "리액트"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("React"))
                .andExpect(jsonPath("$.category").value("framework"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.levelName").value("학습 중"));
    }

    @Test
    void 이미_있는_기술을_다시_추가하면_200과_기존_항목을_돌려준다() throws Exception {
        Long member = newMember();
        Long firstId = idOf(add(member, """
                {"name": "Kafka", "level": 3}
                """).andExpect(status().isCreated()));

        add(member, """
                {"name": "apache kafka"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId))
                .andExpect(jsonPath("$.level").value(3));
    }

    @Test
    void 사전에_없는_기술은_카테고리가_있어야_추가되고_자동완성에는_나오지_않는다() throws Exception {
        Long member = newMember();
        String name = unique("Spring Cloud Gateway");

        add(member, """
                {"name": "%s"}
                """.formatted(name))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_INPUT"));

        add(member, """
                {"name": "%s", "category": "FRAMEWORK"}
                """.formatted(name))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name));

        mvc.perform(get("/api/skills").param("query", name))
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));
        Integer logged = jdbc.sql("SELECT count(*) FROM app.unregistered_skill_name WHERE raw_name = :name AND source = 'PROFILE'")
                .param("name", name).query(Integer.class).single();
        assertThat(logged).isEqualTo(1);
    }

    @Test
    void 숙련도를_바꾼다() throws Exception {
        Long member = newMember();
        Long id = idOf(add(member, """
                {"name": "Redis"}
                """));

        mvc.perform(patch("/api/me/skills/{id}", id).header(MEMBER_HEADER, member)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"level": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.levelName").value("능숙"));
    }

    @Test
    void 남의_기술은_찾을_수_없다() throws Exception {
        Long owner = newMember();
        Long other = newMember();
        Long id = idOf(add(owner, """
                {"name": "Redis"}
                """));

        mvc.perform(patch("/api/me/skills/{id}", id).header(MEMBER_HEADER, other)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"level": 2}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/skills/{id}", id).header(MEMBER_HEADER, other))
                .andExpect(status().isNotFound());
    }

    @Test
    void 경력에서_쓰는_기술은_지울_수_없고_사용처를_알려준다() throws Exception {
        Long member = newMember();
        Long skillId = idOf(add(member, """
                {"name": "Java"}
                """));
        mvc.perform(post("/api/me/careers").header(MEMBER_HEADER, member)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"companyName": "OO커머스", "teamName": "결제플랫폼팀", "jobTitle": "백엔드 개발자",
                                 "employmentType": "FULL_TIME", "startMonth": "2023-03", "userSkillIds": [%d]}
                                """.formatted(skillId)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/me/skills/{id}", skillId).header(MEMBER_HEADER, member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("IN_USE"))
                .andExpect(jsonPath("$.detail").value("OO커머스 경력에서 사용 중이라 삭제할 수 없어요."))
                .andExpect(jsonPath("$.usages[0].type").value("career"))
                .andExpect(jsonPath("$.usages[0].name").value("OO커머스"));
    }

    @Test
    void 프로젝트에서_쓰는_기술도_지울_수_없다() throws Exception {
        Long member = newMember();
        Long skillId = idOf(add(member, """
                {"name": "Kubernetes"}
                """));
        Long projectId = insertProject(member, "결제 승인 API 성능 개선", null);
        jdbc.sql("INSERT INTO app.project_skill (project_id, user_skill_id) VALUES (:p, :s)")
                .param("p", projectId).param("s", skillId).update();

        mvc.perform(delete("/api/me/skills/{id}", skillId).header(MEMBER_HEADER, member))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.usages[0].type").value("project"))
                .andExpect(jsonPath("$.usages[0].id").value(projectId));
    }

    @Test
    void 쓰지_않는_기술은_지워진다() throws Exception {
        Long member = newMember();
        Long id = idOf(add(member, """
                {"name": "Docker"}
                """));

        mvc.perform(delete("/api/me/skills/{id}", id).header(MEMBER_HEADER, member))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/skills").header(MEMBER_HEADER, member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void 회원_헤더가_없으면_401이다() throws Exception {
        mvc.perform(get("/api/me/skills"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"));
    }

    private ResultActions add(Long member, String body) throws Exception {
        return mvc.perform(post("/api/me/skills").header(MEMBER_HEADER, member)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static Long idOf(ResultActions result) throws Exception {
        Number id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
