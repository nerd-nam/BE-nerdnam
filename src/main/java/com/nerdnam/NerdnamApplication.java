package com.nerdnam;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;

/**
 * Redis 는 세션 저장소·캐시로만 쓴다. Redis 리포지토리 기능은 끈다 — 켜 두면 JPA 리포지토리마다
 * "Could not safely identify store assignment" 안내가 부팅 로그에 쌓인다.
 */
@SpringBootApplication(exclude = DataRedisRepositoriesAutoConfiguration.class)
@ConfigurationPropertiesScan
public class NerdnamApplication {

    public static void main(String[] args) {
        SpringApplication.run(NerdnamApplication.class, args);
    }
}
