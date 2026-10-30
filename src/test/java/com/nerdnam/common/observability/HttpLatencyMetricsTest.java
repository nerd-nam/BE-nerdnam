package com.nerdnam.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.TestcontainersConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

/**
 * 요청 응답 시간이 정해 둔 구간별 개수로 나가서 대시보드가 p95 를 그릴 수 있다 (BE #54).
 *
 * <p>MockMvc 는 HTTP 요청 지표를 만들지 않으므로 실제 서버를 띄우고, 운영처럼 관리 포트를 따로 연다.
 * probe·지표 수집 요청(actuator)은 10초마다 들어오므로 지표에 섞이면 안 된다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "nerdnam.ai-job.sync=true"})
@Import(TestcontainersConfiguration.class)
class HttpLatencyMetricsTest {

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    @Autowired
    ApplicationContext context;

    @Test
    void API_응답_시간은_정한_구간으로만_나가고_actuator_요청은_빠진다() {
        RestClient api = RestClient.create("http://localhost:" + port);
        RestClient management = RestClient.create("http://localhost:" + managementPort);

        api.get().uri("/api/projects/{id}", 999_999_999L).header("X-Member-Id", "1")
                .exchange((request, response) -> null);
        management.get().uri("/actuator/health/readiness").retrieve().toBodilessEntity();
        String metrics = management.get().uri("/actuator/prometheus").retrieve().body(String.class);

        String[] buckets = Arrays.stream(metrics.split("\n"))
                .filter(line -> line.startsWith("http_server_requests_seconds_bucket{")
                        && line.contains("uri=\"/api/projects/{projectId}\""))
                .map(line -> line.replaceAll(".*le=\"([^\"]+)\".*", "$1"))
                .toArray(String[]::new);
        // uri 는 경로 템플릿 — 실제 id 가 들어가면 시계열이 끝없이 늘어난다
        assertThat(buckets).containsExactlyInAnyOrder("0.1", "0.3", "1.0", "3.0", "10.0", "+Inf");

        assertThat(metrics).doesNotContain("uri=\"/actuator");
    }

    @Test
    void 지표를_OTLP_로_밀어_보내지_않는다() {
        // 지표는 Prometheus 가 긁어 간다. 켜져 있으면 받는 곳 없는 localhost:4318 로 1분마다 보내다 실패 로그를 남긴다
        // 실행에만 들어오는 라이브러리라 클래스 이름으로 본다
        assertThat(context.getBeansOfType(MeterRegistry.class).values())
                .map(registry -> registry.getClass().getSimpleName())
                .doesNotContain("OtlpMeterRegistry");
    }
}
