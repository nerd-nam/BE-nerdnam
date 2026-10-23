package com.nerdnam.member.auth;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.member.domain.SocialProvider;
import java.util.Map;

/**
 * 소셜 제공자가 돌려준 사용자 정보에서 필요한 것만 꺼낸다. 제공자마다 모양이 다르다.
 * <ul>
 *   <li>Google: {@code sub}, {@code email}, {@code name}</li>
 *   <li>카카오: {@code id}, {@code kakao_account.email}, {@code kakao_account.profile.nickname}
 *       (없으면 {@code properties.nickname})</li>
 * </ul>
 */
public record SocialProfile(SocialProvider provider, String providerUserId, String email, String name) {

    public static SocialProfile from(String registrationId, Map<String, Object> attributes) {
        SocialProvider provider = SocialProvider.fromRegistrationId(registrationId);
        return switch (provider) {
            case GOOGLE -> new SocialProfile(provider, required(attributes.get("sub")), text(attributes.get("email")),
                    text(attributes.get("name")));
            case KAKAO -> {
                Map<?, ?> account = map(attributes.get("kakao_account"));
                Map<?, ?> profile = map(account.get("profile"));
                String nickname = text(profile.get("nickname"));
                if (nickname == null) {
                    nickname = text(map(attributes.get("properties")).get("nickname"));
                }
                yield new SocialProfile(provider, required(attributes.get("id")), text(account.get("email")), nickname);
            }
        };
    }

    private static String required(Object value) {
        String text = text(value);
        if (text == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "로그인 정보를 확인하지 못했어요.");
        }
        return text;
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> m ? m : Map.of();
    }
}
