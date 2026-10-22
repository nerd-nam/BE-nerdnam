package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.auth.LocalHeaderMemberIdentityProvider;
import com.nerdnam.member.domain.SocialProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 제공자별 사용자 정보 꺼내기, 개발용 헤더 인증의 안전장치 */
class SocialProfileTest {

    @Test
    void 구글() {
        SocialProfile p = SocialProfile.from("google", Map.of("sub", "g-1", "email", "a@b.com", "name", "홍길동"));

        assertThat(p).isEqualTo(new SocialProfile(SocialProvider.GOOGLE, "g-1", "a@b.com", "홍길동"));
    }

    @Test
    void 카카오는_계정_프로필_닉네임을_쓰고_없으면_properties_닉네임() {
        SocialProfile p = SocialProfile.from("kakao", Map.of("id", 123456789L,
                "kakao_account", Map.of("email", "k@kakao.com", "profile", Map.of("nickname", "카카오"))));
        SocialProfile old = SocialProfile.from("kakao", Map.of("id", 1L, "properties", Map.of("nickname", "옛닉네임")));

        assertThat(p).isEqualTo(new SocialProfile(SocialProvider.KAKAO, "123456789", "k@kakao.com", "카카오"));
        assertThat(old.name()).isEqualTo("옛닉네임");
        assertThat(old.email()).isNull();
    }

    @Test
    void 사용자_id가_없거나_모르는_제공자면_로그인_실패() {
        assertThatThrownBy(() -> SocialProfile.from("google", Map.of("email", "a@b.com")))
                .hasMessageContaining("로그인 정보를 확인하지 못했어요");
        assertThatThrownBy(() -> SocialProfile.from("github", Map.of("id", 1)))
                .hasMessageContaining("지원하지 않는 로그인");
    }

    @Test
    void 헤더_인증은_local_프로필이_아니면_앱이_뜨지_않는다() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        MockEnvironment local = new MockEnvironment();
        local.setDefaultProfiles("local");

        assertThatThrownBy(() -> new LocalHeaderMemberIdentityProvider(prod)).isInstanceOf(IllegalStateException.class);
        new LocalHeaderMemberIdentityProvider(local);
    }
}
