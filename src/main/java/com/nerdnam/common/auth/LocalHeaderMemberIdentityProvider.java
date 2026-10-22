package com.nerdnam.common.auth;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 로그인(BE-03)이 생기기 전 개발용: {@code X-Member-Id} 요청 헤더의 값을 회원 id 로 쓴다.
 *
 * <p>{@code nerdnam.auth.mode=header}일 때만 켜지고(application-local.yml), <b>local 프로필이 아니면 앱이 뜨지 않는다.</b>
 * 운영에서 켜지면 누구나 남의 id 로 요청할 수 있기 때문이다. 기본은 로그인 세션
 * (member.auth.SessionMemberIdentityProvider). 로컬 DB 에는 1번 회원이 미리 들어 있다 (db/local/R__local_seed.sql).
 */
@Component
@ConditionalOnProperty(name = "nerdnam.auth.mode", havingValue = "header")
public class LocalHeaderMemberIdentityProvider implements MemberIdentityProvider {

    public static final String HEADER = "X-Member-Id";

    public LocalHeaderMemberIdentityProvider(Environment environment) {
        String[] effective = environment.getActiveProfiles().length > 0
                ? environment.getActiveProfiles() : environment.getDefaultProfiles();
        if (Arrays.stream(effective).noneMatch("local"::equals)) {
            throw new IllegalStateException("nerdnam.auth.mode=header 는 local 프로필에서만 쓸 수 있어요.");
        }
    }

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
