package com.nerdnam.member.auth;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.actuate.autoconfigure.web.server.ConditionalOnManagementPort;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementPortType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 관리 포트(기본 8081) 전용 보안 (BE #42·#44).
 *
 * <p>actuator 를 공개 포트에서 떼어 관리 포트에만 두었다. 그 포트는 Ingress 에 넣지 않고
 * Kubernetes probe 와 Prometheus 만 NetworkPolicy 로 들어온다. 그래서 이 체인은 열어 두고,
 * <b>공개 포트(8080)에는 actuator 가 아예 없다</b> — 앱 전체를 permitAll 로 바꾸지 않는다.
 *
 * <p>{@code ConditionalOnManagementPort(DIFFERENT)} 라서 관리 포트를 따로 두지 않은 환경
 * (테스트·로컬에서 포트를 같게 둔 경우)에는 이 체인이 만들어지지 않고, 그때는
 * {@link SecurityConfig} 의 actuator 허용 규칙이 쓰인다.
 */
@Configuration
@ConditionalOnManagementPort(ManagementPortType.DIFFERENT)
public class ManagementSecurityConfig {

    @Bean
    @Order(0)   // 공개 포트 체인보다 먼저 본다
    SecurityFilterChain managementSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())          // 읽기 전용 endpoint 뿐이다
                .cors(Customizer.withDefaults())
                .build();
    }
}
