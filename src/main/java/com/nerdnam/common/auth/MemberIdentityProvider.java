package com.nerdnam.common.auth;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청에서 회원 id 를 찾는 방법. 찾지 못하면 {@code ErrorCode.UNAUTHORIZED} 예외를 던진다.
 */
public interface MemberIdentityProvider {

    Long currentMemberId(HttpServletRequest request);
}
