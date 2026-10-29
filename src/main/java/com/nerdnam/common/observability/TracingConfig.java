package com.nerdnam.common.observability;

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
}
