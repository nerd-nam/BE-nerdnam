package com.nerdnam.member.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * 소셜 로그인 성공: 세션 수명을 로그인한 세션 값(기본 7일)으로 늘리고 웹으로 돌려보낸다.
 *
 * <p>세션의 기본 수명({@code spring.session.timeout})은 짧다(10분). 로그인 전에도 세션이 생기는 곳이 있기 때문이다 —
 * {@code /oauth2/authorization/*} 는 OAuth {@code state} 를 세션에 저장한다. 그 세션이 7일 남으면, 이 주소를
 * 반복해서 부르는 것만으로 Redis 가 차서 모든 세션 쓰기가 실패한다(BE #62 와 같은 장애). 로그인을 끝낸 세션만 길게 둔다.
 */
class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthProperties auth;

    LoginSuccessHandler(AuthProperties auth) {
        this.auth = auth;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        request.getSession().setMaxInactiveInterval(Math.toIntExact(auth.sessionTimeout().toSeconds()));
        response.sendRedirect(auth.loginSuccessUrl());
    }
}
