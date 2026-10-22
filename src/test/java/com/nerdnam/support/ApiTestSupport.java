package com.nerdnam.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.nerdnam.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API 통합 테스트의 공통 부모. 실제 PostgreSQL(+pgvector) 컨테이너에 앱 전체를 띄운다.
 * 같은 설정을 쓰는 테스트 클래스끼리는 Spring 이 컨텍스트(와 컨테이너)를 재사용하므로 한 번만 뜬다.
 *
 * <p>테스트끼리 데이터가 섞이지 않도록 테스트마다 {@link #newMember()}로 새 회원을 만들어 쓴다.
 * AI 작업은 별도 스레드 대신 요청 스레드에서 바로 실행되므로(nerdnam.ai-job.sync), 202 응답을 받으면 작업이 이미 끝나 있다.
 */
@SpringBootTest(properties = "nerdnam.ai-job.sync=true")
@Import(TestcontainersConfiguration.class)
public abstract class ApiTestSupport {

    protected static final String MEMBER_HEADER = "X-Member-Id";

    @Autowired
    protected WebApplicationContext context;

    @Autowired
    protected JdbcClient jdbc;

    protected MockMvc mvc;

    @BeforeEach
    void setUpMockMvc() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    protected Long newMember() {
        return jdbc.sql("INSERT INTO app.member (display_name) VALUES ('테스트 회원') RETURNING id")
                .query(Long.class)
                .single();
    }

    protected ResultActions getJson(Long memberId, String url, Object... vars) throws Exception {
        return mvc.perform(get(url, vars).header(MEMBER_HEADER, memberId));
    }

    protected ResultActions postJson(Long memberId, String body, String url, Object... vars) throws Exception {
        return mvc.perform(post(url, vars).header(MEMBER_HEADER, memberId)
                .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body));
    }

    protected ResultActions putJson(Long memberId, String body, String url, Object... vars) throws Exception {
        return mvc.perform(put(url, vars).header(MEMBER_HEADER, memberId)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** 응답 JSON 에서 값 하나 */
    protected static <T> T read(ResultActions result, String path) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), path);
    }

    protected static Long readLong(ResultActions result, String path) throws Exception {
        Object value = read(result, path);
        return value == null ? null : ((Number) value).longValue();
    }

    /** 기술스택에 기술 하나 추가하고 기술스택 항목 id 를 돌려준다 */
    protected Long addSkill(Long memberId, String name) throws Exception {
        return readLong(postJson(memberId, """
                {"name": "%s"}
                """.formatted(name), "/api/me/skills"), "$.id");
    }

    /** 사전 공용 테이블에 남아도 다른 테스트와 겹치지 않는 이름 */
    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 프로젝트 행을 SQL 로 바로 넣는다 (기술·경력 삭제 규칙처럼 프로젝트 API 를 거칠 필요가 없는 테스트용) */
    protected Long insertProject(Long memberId, String title, Long careerId) {
        return jdbc.sql("""
                        INSERT INTO app.project (member_id, title, affiliation, career_id)
                        VALUES (:memberId, :title, :affiliation, :careerId) RETURNING id
                        """)
                .param("memberId", memberId)
                .param("title", title)
                .param("affiliation", careerId == null ? "PERSONAL" : "CAREER")
                .param("careerId", careerId)
                .query(Long.class)
                .single();
    }
}
