package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

/** 로그인에 성공한 세션만 길게(7일) 둔다. 로그인 전 세션은 짧다(spring.session.timeout 10분, BE #62 후속) */
class LoginSuccessHandlerTest {

    @Test
    void 로그인하면_세션을_7일로_늘리고_웹으로_돌려보낸다() throws Exception {
        AuthProperties auth = new AuthProperties("session", "1", "https://web.test", null, null, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setMaxInactiveInterval((int) Duration.ofMinutes(10).toSeconds());   // 로그인 전: OAuth state 만 든 세션
        request.setSession(session);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new LoginSuccessHandler(auth).onAuthenticationSuccess(request, response, null);

        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofDays(7).toSeconds());
        assertThat(response.getRedirectedUrl()).isEqualTo("https://web.test/");
    }
}
