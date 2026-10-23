package com.nerdnam.member.auth;

import com.nerdnam.common.auth.MemberIdentityProvider;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/** 로그인 세션(Spring Security)의 회원 id 를 쓴다 — 기본 방식 (nerdnam.auth.mode=session) */
@Component
@ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "session", matchIfMissing = true)
public class SessionMemberIdentityProvider implements MemberIdentityProvider {

    @Override
    public Long currentMemberId(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof OAuth2User user
                && user.getAttribute(MemberOAuth2UserService.MEMBER_ID) instanceof Number id) {
            return id.longValue();
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
