package com.nerdnam.aijob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.aijob.application.AiJobDrain;
import com.nerdnam.support.ApiTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * 배포·재시작 때 AI 작업을 비우는 절차 (BE #45).
 *
 * <p>접수를 멈추면 새 작업이 "잠시 뒤 다시 시도해 주세요"(503)로 거절된다 — 계약에 있는 코드라
 * 웹이 이미 다루는 상태다. 사용자가 다시 누르면 교체된 Pod 가 받는다.
 *
 * <p>노출 설정을 테스트에서 따로 열지 않는다 — 운영 설정(application.yml)이 aijob 을 여는지까지 확인하려는 것이다.
 * (처음 구현 때 그 설정이 빠졌는데 테스트가 따로 열어 통과해 버렸다.)
 */
class AiJobDrainTest extends ApiTestSupport {

    @Autowired
    AiJobDrain drain;

    @AfterEach
    void resume() {
        drain.resume();   // 다른 테스트가 영향을 받지 않게 되돌린다
    }

    @Test
    void 접수를_멈추면_새_AI_작업을_503으로_거절한다() throws Exception {
        Long member = newMember();
        Long project = readLong(postJson(member, """
                {"title": "배포 중 접수 확인", "affiliation": "PERSONAL", "rawText": "원문 한 줄"}
                """, "/api/projects"), "$.id");

        drain.drain();

        postJson(member, null, "/api/projects/{id}/split", project)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("AI_UNAVAILABLE"));

        drain.resume();   // 배포를 취소하면 다시 받는다
        postJson(member, null, "/api/projects/{id}/split", project).andExpect(status().isAccepted());
    }

    @Test
    void 관리_endpoint가_상태를_알려_주고_접수를_멈출_수_있다() throws Exception {
        assertThat(drain.draining()).isFalse();

        mvc.perform(post("/actuator/aijob").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"drain\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draining").value(true));
        assertThat(drain.draining()).isTrue();

        // 비었는지 보고 배포한다 — 테스트는 작업을 요청 스레드에서 바로 끝내므로 0 이다
        mvc.perform(get("/actuator/aijob"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.drained").value(true))
                .andExpect(jsonPath("$.inFlight").value(0));

        mvc.perform(post("/actuator/aijob").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\": \"resume\"}"))
                .andExpect(jsonPath("$.draining").value(false));
    }
}
