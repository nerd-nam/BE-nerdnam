package com.nerdnam.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.support.ApiTestSupport;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * 앱 안에서 만들어지는 span (BE #51): AI 작업 span 과 작업 id 속성.
 * 내보내기는 끄고(수집기 없음) 만들어지는 observation 만 본다.
 */
@TestPropertySource(properties = "management.tracing.sampling.probability=1.0")
class TracingIntegrationTest extends ApiTestSupport {

    @Autowired
    ObservationRegistry registry;

    private final List<Observation.Context> seen = new CopyOnWriteArrayList<>();
    private static boolean installed;

    @BeforeEach
    void capture() {
        seen.clear();
        if (!installed) {   // 문맥은 테스트끼리 공유된다 — 처리기는 한 번만 단다
            registry.observationConfig().observationHandler(new ObservationHandler<>() {
                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }

                @Override
                public void onStop(Observation.Context context) {
                    CAPTURED.add(context);
                }
            });
            installed = true;
        }
        CAPTURED.clear();
    }

    static final List<Observation.Context> CAPTURED = new CopyOnWriteArrayList<>();

    @Test
    void AI_작업은_별도_span이고_작업_id를_속성으로_가진다() throws Exception {
        Long member = newMember();
        Long project = readLong(postJson(member, """
                {"title": "추적 확인", "affiliation": "PERSONAL", "rawText": "응답이 1.2초였다. 캐시로 0.3초가 됐다."}
                """, "/api/projects"), "$.id");
        Long jobId = readLong(postJson(member, null, "/api/projects/{id}/split", project), "$.jobId");

        Observation.Context job = CAPTURED.stream().filter(c -> "nerdnam.ai.job".equals(c.getName())).findFirst()
                .orElseThrow(() -> new AssertionError("AI 작업 span 이 없다"));
        assertThat(job.getHighCardinalityKeyValue("job.id").getValue()).isEqualTo(String.valueOf(jobId));
        assertThat(job.getLowCardinalityKeyValue("job.type").getValue()).isEqualTo("restructure");
        // 작업 id 는 지표 label(low cardinality)로 쓰지 않는다 — 시계열이 무한히 늘어난다
        assertThat(job.getLowCardinalityKeyValue("job.id")).isNull();
    }

}
