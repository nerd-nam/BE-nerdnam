package com.nerdnam.aijob;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영 지표가 Prometheus 모양으로 나간다 (BE #44).
 *
 * <p>운영에서는 {@code MANAGEMENT_PORT} 로 공개 포트와 분리한다(그러면 공개 포트에 actuator 가 없다).
 * 여기서는 같은 포트에서 지표 이름과 label 규칙만 확인한다.
 */
@TestPropertySource(properties = "management.endpoints.web.exposure.include=health,info,prometheus")
class AiJobMetricsTest extends ApiTestSupport {

    @Test
    void AI_작업과_임베딩_대기_지표가_나간다() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("nerdnam_aijob_in_progress"),
                        containsString("nerdnam_aijob_failed_recent"),
                        containsString("nerdnam_chunk_pending"),
                        containsString("nerdnam_chunk_pending_oldest_seconds"))))
                // label 에 member_id·작업 id 를 쓰지 않는다 — 시계열이 무한히 늘어난다 (BE #44)
                .andExpect(content().string(not(containsString("member_id="))));
    }
}
