package com.nerdnam.member.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.Locale;

/** 소셜 로그인 제공자 (D-8). OAuth2 등록 이름(google, kakao)과 같다 */
public enum SocialProvider {
    GOOGLE,
    KAKAO;

    public static SocialProvider fromRegistrationId(String registrationId) {
        try {
            return valueOf(registrationId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "지원하지 않는 로그인 방식이에요.");
        }
    }
}
