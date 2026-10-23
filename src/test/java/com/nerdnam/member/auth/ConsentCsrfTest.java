package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.TestcontainersConfiguration;
import com.nerdnam.member.application.MemberService;
import com.nerdnam.member.domain.SocialProvider;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** csrf() 테스트 도우미가 토큰 저장소를 바꾸므로 별도 컨텍스트에서 실제 쿠키 방식을 확인한다. */
@SpringBootTest(properties = {"nerdnam.auth.mode=session", "nerdnam.auth.terms-version=csrf-test"})
@Import(TestcontainersConfiguration.class)
class ConsentCsrfTest {

    @Autowired
    WebApplicationContext context;

    @Autowired
    MemberService members;

    @Test
    void 공개_CSRF_토큰과_쿠키로_실제_동의_요청을_보낼_수_있다() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        Cookie cookie = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty()).andReturn().getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        Long member = members.loginWithSocial(new SocialProfile(SocialProvider.GOOGLE, UUID.randomUUID().toString(), null, "동의 회원"));
        mvc.perform(post("/api/me/consents").with(oauth2Login().attributes(a -> a.put(MemberOAuth2UserService.MEMBER_ID, member)))
                        .cookie(cookie).header("X-XSRF-TOKEN", "잘못된 토큰"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/consents").with(oauth2Login().attributes(a -> a.put(MemberOAuth2UserService.MEMBER_ID, member)))
                        .cookie(cookie).header("X-XSRF-TOKEN", cookie.getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.consentRequired").value(false));
    }
}
