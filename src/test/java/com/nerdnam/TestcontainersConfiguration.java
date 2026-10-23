package com.nerdnam;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL(+pgvector) 컨테이너.
 * {@code @Import(TestcontainersConfiguration.class)} 를 붙인 테스트는 로컬 DB 대신 이 컨테이너에 연결된다.
 * Docker Desktop 이 켜져 있어야 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("pgvector/pgvector:pg17");
    }
}
