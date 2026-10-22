package com.nerdnam.member.auth;

import com.nerdnam.member.application.MemberService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 소셜 로그인 완료 → 제공자 사용자 정보로 회원을 찾거나 만들고, 세션에 회원 id 를 담는다 (속성 {@value #MEMBER_ID}).
 * 컨트롤러는 {@link SessionMemberIdentityProvider}가 이 값을 꺼내 {@code @CurrentMemberId}로 받는다.
 */
public class MemberOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String MEMBER_ID = "nerdnamMemberId";

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;
    private final MemberService members;

    public MemberOAuth2UserService(MemberService members) {
        this(new DefaultOAuth2UserService(), members);
    }

    MemberOAuth2UserService(OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate, MemberService members) {
        this.delegate = delegate;
        this.members = members;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        SocialProfile profile = SocialProfile.from(request.getClientRegistration().getRegistrationId(), user.getAttributes());
        Long memberId = members.loginWithSocial(profile);
        Map<String, Object> attributes = new HashMap<>(user.getAttributes());
        attributes.put(MEMBER_ID, memberId);
        return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")), attributes, MEMBER_ID);
    }
}
