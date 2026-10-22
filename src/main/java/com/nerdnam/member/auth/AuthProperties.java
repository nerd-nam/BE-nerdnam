package com.nerdnam.member.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * nerdnam.auth.*
 *
 * @param mode            session = 소셜 로그인 쿠키 세션 (기본) / header = X-Member-Id 헤더 (local 프로필 전용, 개발용)
 * @param termsVersion    지금 약관 버전. 이 버전에 동의하지 않은 회원은 consentRequired = true
 * @param webOrigin       웹 주소 (CORS 허용, 쿠키를 함께 보내는 요청)
 * @param loginSuccessUrl 소셜 로그인 성공 후 돌아갈 웹 주소
 * @param loginFailureUrl 소셜 로그인 실패 후 돌아갈 웹 주소
 */
@ConfigurationProperties(prefix = "nerdnam.auth")
public record AuthProperties(String mode, String termsVersion, String webOrigin, String loginSuccessUrl,
                             String loginFailureUrl) {

    public AuthProperties {
        mode = mode == null ? "session" : mode;
        termsVersion = termsVersion == null ? "1" : termsVersion;
        webOrigin = webOrigin == null ? "http://localhost:3000" : webOrigin;
        loginSuccessUrl = loginSuccessUrl == null ? webOrigin + "/" : loginSuccessUrl;
        loginFailureUrl = loginFailureUrl == null ? webOrigin + "/?login=failed" : loginFailureUrl;
    }
}
