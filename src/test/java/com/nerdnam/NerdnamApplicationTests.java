package com.nerdnam;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 애플리케이션이 뜨고, Flyway 마이그레이션이 실제 PostgreSQL+pgvector 에 적용되는지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NerdnamApplicationTests {

    @Autowired
    JdbcClient jdbc;

    @Test
    @DisplayName("Flyway 가 app·rag 스키마의 테이블 27개를 만든다")
    void migrationsApplied() {
        Integer tables = jdbc.sql("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema IN ('app', 'rag') AND table_name <> 'flyway_schema_history'
                        """)
                .query(Integer.class)
                .single();

        assertThat(tables).isEqualTo(27);
    }

    @Test
    @DisplayName("pgvector 확장이 설치되어 벡터 거리 계산을 할 수 있다")
    void pgvectorInstalled() {
        Double distance = jdbc.sql("SELECT '[1,0,0]'::vector <=> '[0,1,0]'::vector")
                .query(Double.class)
                .single();

        assertThat(distance).isEqualTo(1.0);
    }

    @Test
    @DisplayName("표준 기술 사전 시드가 들어가 있다")
    void skillCatalogSeeded() {
        String name = jdbc.sql("""
                        SELECT s.name FROM app.skill_alias a JOIN app.skill s ON s.id = a.skill_id
                        WHERE a.alias_key = :key
                        """)
                .param("key", "리액트")
                .query(String.class)
                .single();

        assertThat(name).isEqualTo("React");
    }
}
