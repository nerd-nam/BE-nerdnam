package com.nerdnam.member.auth;

import com.nerdnam.common.auth.MemberIdentityProvider;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.member.application.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 인증·CSRF 확인 뒤, 현재 약관에 동의하지 않은 회원의 서비스 API를 막는다. 개발용 헤더 인증은 제외한다. */
@Configuration
@ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "session", matchIfMissing = true)
public class ConsentWebConfig implements WebMvcConfigurer, HandlerInterceptor {

    private final MemberIdentityProvider identity;
    private final MemberService members;

    public ConsentWebConfig(MemberIdentityProvider identity, MemberService members) {
        this.identity = identity;
        this.members = members;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String method = request.getMethod();
        boolean read = method.equals("GET") || method.equals("HEAD");
        // 동의 상태 조회·기록·탈퇴·로그아웃과 CSRF 준비는 미동의 상태에서도 가능해야 한다.
        if ((path.equals("/api/me") && (read || method.equals("DELETE")))
                || (path.equals("/api/me/consents") && method.equals("POST"))
                || (path.equals("/api/auth/logout") && method.equals("POST"))
                || (path.equals("/api/auth/csrf") && read)) {
            return true;
        }
        MemberService.MemberView member = members.me(identity.currentMemberId(request));
        if (member.consentRequired()) {
            throw new BusinessException(ErrorCode.CONSENT_REQUIRED, ErrorCode.CONSENT_REQUIRED.defaultMessage(),
                    Map.of("termsVersion", member.termsVersion()));
        }
        return true;
    }
}
