package com.nerdnam.member.auth;

import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.member.application.MemberService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 로그인 설정 (BE-03). 방식은 nerdnam.auth.mode 로 고른다.
 * <ul>
 *   <li><b>session</b> (기본): Google·카카오 소셜 로그인 → 쿠키 세션. /api/** 는 로그인 필요(401 ProblemDetail).
 *       상태를 바꾸는 요청은 CSRF 토큰 필요: 웹은 XSRF-TOKEN 쿠키 값을 X-XSRF-TOKEN 헤더로 보낸다.</li>
 *   <li><b>header</b> (local 전용): 로그인 없이 X-Member-Id 헤더로 회원을 고른다 (개발용).</li>
 * </ul>
 * 로그인 시작: 웹이 {@code /oauth2/authorization/google}(또는 kakao)로 이동 → 성공하면 nerdnam.auth.login-success-url 로 돌아간다.
 * 로그아웃: {@code POST /api/auth/logout} → 204.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "session", matchIfMissing = true)
    SecurityFilterChain sessionSecurity(HttpSecurity http, MemberService members, AuthProperties auth) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll()
                        .requestMatchers("/oauth2/**", "/login/**").permitAll()
                        .requestMatchers("/api/auth/csrf").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> problem(res, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((req, res, ex) -> problem(res, ErrorCode.FORBIDDEN)))
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.userService(new MemberOAuth2UserService(members)))
                        .successHandler((req, res, authentication) -> res.sendRedirect(auth.loginSuccessUrl()))
                        .failureHandler((req, res, ex) -> res.sendRedirect(auth.loginFailureUrl())))
                .logout(l -> l
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((req, res, authentication) -> res.setStatus(HttpStatus.NO_CONTENT.value())));
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "header")
    SecurityFilterChain headerSecurity(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(a -> a.anyRequest().permitAll());
        return http.build();
    }

    /** 웹(다른 출처)에서 쿠키를 함께 보내는 요청을 허용한다. 배포 때 쿠키 도메인·SameSite 를 웹 주소에 맞춘다 */
    @Bean
    CorsConfigurationSource corsConfigurationSource(AuthProperties auth) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(auth.webOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 다른 API 오류와 같은 ProblemDetail 모양 */
    private static void problem(HttpServletResponse res, ErrorCode code) throws IOException {
        res.setStatus(code.status().value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + code.name() + "\",\"status\":"
                + code.status().value() + ",\"detail\":\"" + code.defaultMessage() + "\"}");
    }
}
