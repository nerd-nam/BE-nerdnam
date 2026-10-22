package com.nerdnam.common.auth;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * local 이 아닌 프로필의 자리 표시자. 로그인(BE-03)을 붙이기 전까지는 모든 요청을 401 로 막는다.
 * BE-03 에서 Spring Security 세션의 회원 id 를 읽는 구현으로 바꾼다.
 */
@Component
@Profile("!local")
public class UnauthenticatedMemberIdentityProvider implements MemberIdentityProvider {

    @Override
    public Long currentMemberId(HttpServletRequest request) {
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
