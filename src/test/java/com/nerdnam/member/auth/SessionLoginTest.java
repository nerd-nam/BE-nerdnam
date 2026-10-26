package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.TestcontainersConfiguration;
import com.nerdnam.member.application.MemberService;
import com.nerdnam.member.domain.SocialProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 기본 방식(소셜 로그인 쿠키 세션)에서의 인증·CSRF·동의·회원 찾기 (BE-03) */
@SpringBootTest(properties = {"nerdnam.auth.mode=session", "nerdnam.ai-job.sync=true"})
@Import(TestcontainersConfiguration.class)
class SessionLoginTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberService members;

    @Autowired
    JdbcClient jdbc;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void 로그인하지_않으면_401_ProblemDetail이고_개발용_헤더는_통하지_않는다() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("UNAUTHORIZED"));
        mvc.perform(get("/api/me").header("X-Member-Id", "1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void 로그인하면_세션의_회원으로_요청하고_처음에는_동의가_필요하다() throws Exception {
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, uid(), null, "구글 사용자"));

        mvc.perform(get("/api/me").with(login(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(member))
                .andExpect(jsonPath("$.consentRequired").value(true));

        mvc.perform(post("/api/me/consents").with(login(member)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN"));
        mvc.perform(post("/api/me/consents").with(login(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false));
        Integer consents = jdbc.sql("SELECT count(*) FROM app.member_consent WHERE member_id = :m").param("m", member)
                .query(Integer.class).single();
        assertThat(consents).isEqualTo(2);
    }

    @Test
    void 다시_로그인해_동의를_재전송해도_기록은_중복되지_않고_하던_단계를_조회한다() throws Exception {
        SocialProfile profile = new SocialProfile(SocialProvider.GOOGLE, uid(), null, "재방문 회원");
        Long member = members.loginWithSocial(profile);
        members.agreeToTerms(member);
        assertThat(members.loginWithSocial(profile)).isEqualTo(member);

        // S01에서 기억한 동의가 로그인 직후 다시 전송될 수 있다 (기획서 v1.41).
        mvc.perform(post("/api/me/consents").with(login(member)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentRequired").value(false));
        mvc.perform(get("/api/me/onboarding").with(login(member)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextStep").value("skills"));
        assertThat(jdbc.sql("SELECT count(*) FROM app.member_consent WHERE member_id = :m")
                .param("m", member).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    void 같은_소셜_계정은_같은_회원이고_이미_쓰인_이메일은_합치지_않는다() {
        String email = uid() + "@mail.com";
        String googleId = uid();
        Long first = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, googleId, email, "A"));
        Long again = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, googleId, email, "A"));
        Long kakao = members.loginWithSocial(new SocialProfile(SocialProvider.KAKAO, uid(), email.toUpperCase(), "B"));

        assertThat(again).isEqualTo(first);
        assertThat(kakao).isNotEqualTo(first);
        assertThat(members.me(kakao).email()).isNull();
    }

    @Test
    void 소셜_사용자_정보로_회원을_찾아_세션_주체에_회원_id를_담는다() {
        String kakaoId = String.valueOf(System.nanoTime());
        MemberOAuth2UserService service = new MemberOAuth2UserService(
                request -> new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
                        Map.of("id", kakaoId, "properties", Map.of("nickname", "카카오")), "id"),
                members);
        ClientRegistration kakao = ClientRegistration.withRegistrationId("kakao")
                .clientId("c").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize").tokenUri("https://kauth.kakao.com/oauth/token")
                .build();

        OAuth2User user = service.loadUser(new OAuth2UserRequest(kakao,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", Instant.now(), Instant.now().plusSeconds(60))));

        Long memberId = ((Number) user.getAttribute(MemberOAuth2UserService.MEMBER_ID)).longValue();
        assertThat(members.me(memberId).displayName()).isEqualTo("카카오");
        assertThat(user.getName()).isEqualTo(String.valueOf(memberId));
    }

    @Test
    void 로그아웃은_204() throws Exception {
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, uid(), null, "A"));

        mvc.perform(post("/api/auth/logout").with(login(member)).with(csrf())).andExpect(status().isNoContent());
    }

    @Test
    void 동의_전에는_조회와_등록을_막고_동의하면_허용한다() throws Exception {
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, uid(), null, "미동의 회원"));
        for (String path : List.of("/api/me/onboarding", "/api/me/skills", "/api/projects", "/api/job-postings")) {
            mvc.perform(get(path).with(login(member)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("CONSENT_REQUIRED"))
                    .andExpect(jsonPath("$.termsVersion").value(members.me(member).termsVersion()));
        }
        mvc.perform(post("/api/projects").with(login(member)).with(csrf())).andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT count(*) FROM app.project WHERE member_id = :m").param("m", member)
                .query(Integer.class).single()).isZero();

        mvc.perform(post("/api/me/consents").with(login(member)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/projects").with(login(member))).andExpect(status().isOk());
        mvc.perform(post("/api/projects").with(login(member)).with(csrf())).andExpect(status().isCreated());
    }

    @Test
    void 이전_약관에만_동의한_기존_회원도_현재_버전에_다시_동의해야_한다() throws Exception {
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, uid(), null, "기존 회원"));
        members.agreeToTerms(member);
        jdbc.sql("UPDATE app.member_consent SET terms_version = '이전-버전' WHERE member_id = :m")
                .param("m", member).update();

        mvc.perform(get("/api/me").with(login(member)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(true));
        mvc.perform(get("/api/job-postings").with(login(member)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("CONSENT_REQUIRED"));
        mvc.perform(post("/api/me/consents").with(login(member)).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/job-postings").with(login(member))).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM app.member_consent WHERE member_id = :m").param("m", member)
                .query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    void 동의하지_않고_탈퇴할_수_있다() throws Exception {
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, uid(), null, "탈퇴 회원"));
        mvc.perform(delete("/api/me").with(login(member)).with(csrf())).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT count(*) FROM app.member WHERE id = :m").param("m", member)
                .query(Integer.class).single()).isZero();
    }

    private static RequestPostProcessor login(Long memberId) {
        return oauth2Login().oauth2User(new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_MEMBER")),
                Map.of(MemberOAuth2UserService.MEMBER_ID, memberId), MemberOAuth2UserService.MEMBER_ID));
    }

    private static String uid() {
        return UUID.randomUUID().toString();
    }
}
