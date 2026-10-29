package com.nerdnam.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * actuator 요청은 trace 에서 뺀다 (BE #51) — probe·지표 수집이 10초마다 들어와 trace 를 채운다.
 *
 * <p>규칙을 직접 확인한다. MockMvc 는 HTTP server observation 을 만들지 않아서, MockMvc 로 actuator 를 부르고
 * "span 이 없다"를 보면 규칙이 없어도 통과해 버린다(처음에 그렇게 썼다가 규칙을 꺼도 통과하는 것을 보고 바꿨다).
 */
class TracingConfigTest {

    private final ObservationPredicate predicate = new TracingConfig().skipActuator();

    private static ServerRequestObservationContext request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        return new ServerRequestObservationContext(request, new MockHttpServletResponse());
    }

    @Test
    void probe와_지표_수집은_뺀다() {
        assertThat(predicate.test("http.server.requests", request("/actuator/health/liveness"))).isFalse();
        assertThat(predicate.test("http.server.requests", request("/actuator/health/readiness"))).isFalse();
        assertThat(predicate.test("http.server.requests", request("/actuator/prometheus"))).isFalse();
    }

    @Test
    void API_요청과_다른_observation은_남긴다() {
        assertThat(predicate.test("http.server.requests", request("/api/projects/1/review"))).isTrue();
        assertThat(predicate.test("nerdnam.ai.job", new Observation.Context())).isTrue();
    }

    /** probe 의 Redis INFO 처럼 부모 없는 Redis 명령은 버리고, API 요청 안의 Redis 호출은 남긴다 */
    @Test
    void 부모_없는_Redis_명령만_버린다() {
        ObservationPredicate redis = new TracingConfig().skipOrphanRedisCommands();
        io.lettuce.core.tracing.LettuceObservationContext orphan = new io.lettuce.core.tracing.LettuceObservationContext("redis");
        assertThat(redis.test("redis", orphan)).isFalse();

        io.lettuce.core.tracing.LettuceObservationContext inRequest = new io.lettuce.core.tracing.LettuceObservationContext("redis");
        inRequest.setParentObservation(io.micrometer.observation.Observation.start("http.server.requests",
                io.micrometer.observation.ObservationRegistry.create()));
        assertThat(redis.test("redis", inRequest)).isTrue();
        assertThat(redis.test("http.server.requests", new Observation.Context())).isTrue();
    }
}
