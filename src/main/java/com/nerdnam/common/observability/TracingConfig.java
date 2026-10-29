package com.nerdnam.common.observability;

import io.lettuce.core.tracing.LettuceObservationContext;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/** 분산 추적 공통 설정 (BE #51) */
@Configuration
public class TracingConfig {

    /**
     * actuator 요청은 trace 에서 뺀다 — probe·{@code /actuator/prometheus} 가 10초마다 들어와 trace 를 채운다.
     * 관리 포트(8081)로 분리해도 같은 경로라 이 규칙 하나로 둘 다 걸러진다. 지표(http.server.requests)도
     * 함께 빠지는데, actuator 의 요청 수는 볼 가치가 없다.
     */
    @Bean
    ObservationPredicate skipActuator() {
        return (name, context) -> !(context instanceof ServerRequestObservationContext server
                && server.getCarrier() != null
                && server.getCarrier().getRequestURI().startsWith("/actuator"));
    }

    /**
     * <b>부모 없는 Redis 명령 span 은 버린다.</b> readiness probe 가 Redis 상태를 {@code INFO} 로 확인하는데, 그 HTTP
     * span 은 위에서 뺐으므로 명령 span 이 부모 없이 남아 probe 마다 trace 를 하나씩 만든다. 연결할 때의
     * {@code HELLO}·{@code CLIENT} 도 같다(로컬 Tempo 에서 실제로 확인함). API 요청 안의 Redis 호출(세션 읽기)은
     * 부모가 있으므로 그대로 남는다.
     */
    @Bean
    ObservationPredicate skipOrphanRedisCommands() {
        return (name, context) -> !(context instanceof LettuceObservationContext && context.getParentObservation() == null);
    }
}
