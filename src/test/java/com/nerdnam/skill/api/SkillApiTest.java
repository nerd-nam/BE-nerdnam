package com.nerdnam.skill.api;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;

/** 표준 기술 사전 자동완성 (V2 시드 기준) */
class SkillApiTest extends ApiTestSupport {

    @Test
    void 별칭으로도_찾는다() throws Exception {
        mvc.perform(get("/api/skills").param("query", "k8s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kubernetes"))
                .andExpect(jsonPath("$[0].category").value("INFRA"));
    }

    @Test
    void 키가_똑같은_것을_먼저_보여준다() throws Exception {
        mvc.perform(get("/api/skills").param("query", "Spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Spring"))
                .andExpect(jsonPath("$[*].name", hasItem("Spring Boot")));
    }

    @Test
    void 카테고리로_거른다() throws Exception {
        mvc.perform(get("/api/skills").param("query", "re").param("category", "DATABASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category", everyItem(is("DATABASE"))))
                .andExpect(jsonPath("$[*].name", hasItem("Redis")));
    }

    @Test
    void 검색어가_비면_빈_목록이다() throws Exception {
        mvc.perform(get("/api/skills").param("query", " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
