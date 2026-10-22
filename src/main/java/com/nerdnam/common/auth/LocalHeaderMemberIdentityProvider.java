package com.nerdnam.common.auth;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 로그인(BE-03)이 생기기 전 개발용: {@code X-Member-Id} 요청 헤더의 값을 회원 id 로 쓴다.
 *
 * <p><b>local 프로필에서만</b> 켜진다. 운영에서 켜지면 누구나 남의 id 로 요청할 수 있으므로,
 * 다른 프로필에서는 {@link UnauthenticatedMemberIdentityProvider}가 대신 쓰인다.
 * 로컬 DB 에는 1번 회원이 미리 들어 있다 (db/local/R__local_seed.sql).
 */
@Component
@Profile("local")
public class LocalHeaderMemberIdentityProvider implements MemberIdentityProvider {

    public static final String HEADER = "X-Member-Id";

    @Override
    public Long currentMemberId(HttpServletRequest request) {
        String value = request.getHeader(HEADER);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "개발용 " + HEADER + " 헤더가 필요해요.");
        }
        try {
            return Long.valueOf(value.strip());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, HEADER + " 헤더는 숫자여야 해요.");
        }
    }
}
