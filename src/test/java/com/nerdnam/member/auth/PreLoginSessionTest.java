package com.nerdnam.member.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.TestcontainersConfiguration;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 로그인을 시작만 하고 끝내지 않은 세션이 Redis 에 오래 남지 않는다 (BE #62 후속).
 *
 * <p>{@code /oauth2/authorization/*} 는 OAuth {@code state} 를 세션에 저장한다. 그 세션이 7일 남으면 쿠키 없이
 * 이 주소를 반복해 부르는 것만으로 Redis(256MiB, noeviction)가 차서 모든 세션 쓰기가 500 이 된다 — 2026-11-03
 * 운영 장애(BE #62)와 같은 모양이다. 실제 서버와 실제 Redis 로 남은 수명을 잰다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "nerdnam.auth.mode=session")
@Import(TestcontainersConfiguration.class)
class PreLoginSessionTest {

    private static final String KEYS = "nerdnam:session:sessions:*";

    @LocalServerPort
    int port;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 로그인을_시작만_한_세션은_10분_안에_사라진다() {
        Set<String> before = new HashSet<>(redis.keys(KEYS));

        int status = noRedirects().get().uri("/oauth2/authorization/google")
                .exchange((request, response) -> response.getStatusCode().value());

        assertThat(status).isEqualTo(302);   // 구글로 보낸다 — 이때 state 를 담은 세션이 생긴다
        Set<String> created = new HashSet<>(redis.keys(KEYS));
        created.removeAll(before);
        assertThat(created).as("state 를 담은 세션").hasSize(1);
        assertThat(Duration.ofSeconds(redis.getExpire(created.iterator().next())))
                .as("남은 수명 (예전에는 7일)").isLessThanOrEqualTo(Duration.ofMinutes(10)).isPositive();
    }

    private RestClient noRedirects() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws java.io.IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        return RestClient.builder().requestFactory(factory).baseUrl("http://localhost:" + port).build();
    }
}
