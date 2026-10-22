package com.nerdnam.support;

import com.nerdnam.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * API 통합 테스트의 공통 부모. 실제 PostgreSQL(+pgvector) 컨테이너에 앱 전체를 띄운다.
 * 같은 설정을 쓰는 테스트 클래스끼리는 Spring 이 컨텍스트(와 컨테이너)를 재사용하므로 한 번만 뜬다.
 *
 * <p>테스트끼리 데이터가 섞이지 않도록 테스트마다 {@link #newMember()}로 새 회원을 만들어 쓴다.
 */
@SpringBootTest
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

    /** 사전 공용 테이블에 남아도 다른 테스트와 겹치지 않는 이름 */
    protected static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** 프로젝트 모듈이 아직 없으므로 프로젝트 행은 SQL 로 넣는다 */
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
