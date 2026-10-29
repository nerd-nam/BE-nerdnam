package com.nerdnam.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.support.ApiTestSupport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영 로그는 한 줄 JSON 이고 줄마다 trace_id·span_id 가 있다 (BE #51).
 * Grafana 는 로그 줄의 {@code trace_id}(32자리 소문자 16진수)를 찾아 Tempo 로 잇는다.
 * 설정은 application-prod.yml 과 같은 값을 쓴다.
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "logging.structured.format.console=logstash",
        "logging.structured.json.rename.traceId=trace_id",
        "logging.structured.json.rename.spanId=span_id",
        "management.tracing.sampling.probability=1.0"})
class TraceLogTest extends ApiTestSupport {

    @Autowired
    ObservationRegistry registry;

    @Test
    void span_안의_로그_줄에_trace_id와_span_id가_들어간다(CapturedOutput output) {
        Observation.createNotStarted("nerdnam.test", registry).observe(() ->
                LoggerFactory.getLogger(TraceLogTest.class).info("추적 로그 확인"));

        String line = output.getOut().lines().filter(l -> l.contains("추적 로그 확인")).findFirst()
                .orElseThrow(() -> new AssertionError("로그 줄이 없다"));
        assertThat(line).startsWith("{");   // 한 줄 JSON
        assertThat(line).containsPattern("\"trace_id\":\"[0-9a-f]{32}\"");
        assertThat(line).containsPattern("\"span_id\":\"[0-9a-f]{16}\"");
        assertThat(line).doesNotContain("\"traceId\"");   // 이름을 바꿨다
    }
}
