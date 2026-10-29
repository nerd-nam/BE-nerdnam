package com.nerdnam.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** 인프라가 넣는 표준 OTEL_* 변수를 Spring 설정으로 옮긴다 (BE #51) */
class OtelEnvironmentPostProcessorTest {

    private static StandardEnvironment env(Map<String, Object> vars) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("vars", vars));
        new OtelEnvironmentPostProcessor().postProcessEnvironment(env, null);
        return env;
    }

    @Test
    void 인프라_계약_그대로면_OTLP_http로_내보낸다() {
        StandardEnvironment env = env(Map.of(
                "OTEL_SERVICE_NAME", "api",
                "OTEL_EXPORTER_OTLP_ENDPOINT", "http://alloy.monitoring.svc.cluster.local:4318",
                "OTEL_EXPORTER_OTLP_PROTOCOL", "http/protobuf",
                "OTEL_TRACES_EXPORTER", "otlp",
                "OTEL_TRACES_SAMPLER", "parentbased_traceidratio",
                "OTEL_TRACES_SAMPLER_ARG", "1.0",
                "OTEL_RESOURCE_ATTRIBUTES", "k8s.cluster.name=dev,k8s.namespace.name=nerdnam,k8s.pod.name=api-0"));

        assertThat(env.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("true");
        // 공통 주소에는 스펙대로 /v1/traces 를 붙인다
        assertThat(env.getProperty("management.opentelemetry.tracing.export.otlp.endpoint"))
                .isEqualTo("http://alloy.monitoring.svc.cluster.local:4318/v1/traces");
        assertThat(env.getProperty("management.opentelemetry.tracing.export.otlp.transport")).isNull();   // 기본 http
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
        assertThat(env.getProperty("management.opentelemetry.resource-attributes.service.name")).isEqualTo("api");
        assertThat(env.getProperty("management.opentelemetry.resource-attributes.k8s.pod.name")).isEqualTo("api-0");
    }

    @Test
    void 주소가_없으면_내보내지_않는다() {   // 로컬·테스트에는 수집기가 없다
        assertThat(env(Map.of()).getProperty("management.tracing.export.otlp.enabled")).isEqualTo("false");
    }

    @Test
    void 내보내기를_none으로_두면_끈다() {
        StandardEnvironment env = env(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://x:4318", "OTEL_TRACES_EXPORTER", "none"));
        assertThat(env.getProperty("management.tracing.export.otlp.enabled")).isEqualTo("false");
    }

    @Test
    void Spring_설정을_직접_주면_그쪽이_이긴다() {
        StandardEnvironment env = env(Map.of("OTEL_TRACES_SAMPLER_ARG", "1.0", "management.tracing.sampling.probability", "0.1"));
        assertThat(env.getProperty("management.tracing.sampling.probability")).isEqualTo("0.1");
    }
}
