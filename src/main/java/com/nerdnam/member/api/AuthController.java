package com.nerdnam.member.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 웹은 로그인 전·후와 로그아웃 후 토큰을 다시 받아 상태를 바꾸는 요청의 헤더에 넣는다. */
@RestController
@ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "session", matchIfMissing = true)
public class AuthController {

    @GetMapping("/api/auth/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    public record CsrfView(String headerName, String token) {
    }
}
