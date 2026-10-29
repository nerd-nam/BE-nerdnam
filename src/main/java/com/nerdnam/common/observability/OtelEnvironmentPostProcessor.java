package com.nerdnam.common.observability;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 표준 OpenTelemetry 환경 변수({@code OTEL_*})를 Spring 설정으로 옮긴다 (BE #51).
 *
 * <p>인프라는 앱 Pod 에 표준 {@code OTEL_*} 변수만 넣는다(INFRA {@code apps.otelEnv}). 앱은 그 값을 읽기만
 * 하고 주소·샘플링을 코드에 고정하지 않는다. 우리는 Java agent 대신 Micrometer Tracing + OTLP 를 쓰는데
 * (executor 전파를 테스트로 확인하고 actuator 를 trace 에서 빼려고), Spring 은 {@code OTEL_*} 를 직접 읽지
 * 않으므로 여기서 옮긴다. <b>가장 낮은 우선순위</b>로 넣어 Spring 설정을 직접 준 쪽이 이긴다.
 *
 * <p>{@code OTEL_EXPORTER_OTLP_ENDPOINT} 가 없으면 내보내지 않는다 — 로컬·테스트에는 수집기가 없다.
 */
public class OtelEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE = "otelEnvironment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> props = new HashMap<>();

        String tracesEndpoint = env.getProperty("OTEL_EXPORTER_OTLP_TRACES_ENDPOINT");
        String endpoint = env.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT");
        String exporter = env.getProperty("OTEL_TRACES_EXPORTER", "otlp").trim().toLowerCase(Locale.ROOT);
        // 스펙: 신호별 변수는 그대로 쓰고, 공통 주소에는 /v1/traces 를 붙인다 (http/protobuf)
        String url = blank(tracesEndpoint) ? (blank(endpoint) ? null : trimSlash(endpoint) + "/v1/traces") : tracesEndpoint;
        boolean export = url != null && !"none".equals(exporter);
        props.put("management.tracing.export.otlp.enabled", export);
        if (url != null) {
            props.put("management.opentelemetry.tracing.export.otlp.endpoint", url);
        }
        String protocol = env.getProperty("OTEL_EXPORTER_OTLP_TRACES_PROTOCOL", env.getProperty("OTEL_EXPORTER_OTLP_PROTOCOL", ""));
        if (protocol.toLowerCase(Locale.ROOT).startsWith("grpc")) {
            props.put("management.opentelemetry.tracing.export.otlp.transport", "grpc");
        }

        // parentbased_traceidratio 는 Micrometer 의 기본 sampler 와 같다 — 비율만 옮긴다
        String ratio = env.getProperty("OTEL_TRACES_SAMPLER_ARG");
        if (!blank(ratio)) {
            props.put("management.tracing.sampling.probability", ratio.trim());
        }

        // 서비스 이름·리소스 속성 (Grafana 에서 Loki 의 app 라벨과 이어진다)
        String resource = env.getProperty("OTEL_RESOURCE_ATTRIBUTES");
        if (!blank(resource)) {
            for (String pair : resource.split(",")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    props.put("management.opentelemetry.resource-attributes." + pair.substring(0, eq).trim(),
                            pair.substring(eq + 1).trim());
                }
            }
        }
        String service = env.getProperty("OTEL_SERVICE_NAME");
        if (!blank(service)) {
            props.put("management.opentelemetry.resource-attributes.service.name", service.trim());
        }

        env.getPropertySources().addLast(new MapPropertySource(SOURCE, props));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimSlash(String value) {
        String v = value.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
