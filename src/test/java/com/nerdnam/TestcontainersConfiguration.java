package com.nerdnam;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 테스트용 PostgreSQL(+pgvector) 컨테이너.
 * {@code @Import(TestcontainersConfiguration.class)} 를 붙인 테스트는 로컬 DB 대신 이 컨테이너에 연결된다.
 * Docker Desktop 이 켜져 있어야 한다.
 *
 * <p>Docker 를 쓸 수 없는 환경(예: CI 의 별도 PostgreSQL)에서는 환경 변수
 * {@code NERDNAM_TEST_USE_LOCAL_DB=true} 를 주면 컨테이너를 띄우지 않고
 * local 프로필의 DB(localhost:5432/nerdnam, pgvector 설치 필요)에 연결한다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "nerdnam.test.use-local-db", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("pgvector/pgvector:pg17");
    }
}
