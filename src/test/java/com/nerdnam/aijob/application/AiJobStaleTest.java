package com.nerdnam.aijob.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.aijob.domain.AiJobRepository;
import com.nerdnam.aijob.domain.FailureCode;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.support.ApiTestSupport;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 오래 멈춘 작업 정리가 살아 있는 작업을 지우지 않는다 (SPEC PR #93 ADR-0008 BE 검토).
 *
 * <p>정리 시간은 접수 시각부터 잰다. AI 서버가 한도에 걸리면 실패 대신 기다리게 되면서 작업이 길어지고,
 * 작업 스레드(4개)가 막혀 큐 대기도 길어진다. 그래서 큐에서 기다리기만 한 작업이 시간에 걸릴 수 있다.
 *
 * <p>실행기를 직접 쥐고(넘긴 작업을 모아 두기만 한다) "큐에서 기다리는 작업"을 그대로 만든다.
 */
class AiJobStaleTest extends ApiTestSupport {

    private static final Duration STALE_AFTER = Duration.ofMinutes(25);

    @Autowired
    AiJobRepository jobs;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final List<Runnable> queued = new ArrayList<>();
    private final AtomicInteger aiCalls = new AtomicInteger();
    private final AtomicReference<FailureCode> targetCleanedWith = new AtomicReference<>();

    @Test
    void 이_Pod_큐에서_기다리는_작업은_시간이_지나도_실패로_지우지_않는다() {
        AiJobs pod = newPod();
        Long jobId = startJob(pod);
        requestedLongAgo(jobId);

        pod.failStaleJobs();

        assertThat(status(jobId)).isEqualTo("IN_PROGRESS");
        queued.getFirst().run();   // 차례가 와서 돈다
        assertThat(aiCalls).hasValue(1);
        assertThat(status(jobId)).isEqualTo("COMPLETED");
    }

    @Test
    void 아무도_들고_있지_않은_작업은_그대로_실패로_정리한다() {
        Long jobId = startJob(newPod());   // 이 Pod 가 내려갔다
        requestedLongAgo(jobId);

        newPod().failStaleJobs();          // 새 Pod 의 정리

        assertThat(status(jobId)).isEqualTo("FAILED");
    }

    @Test
    void 큐에서_기다리는_사이_다른_곳에서_실패로_정리된_작업은_AI_를_부르지_않고_대상만_정리한다() {
        AiJobs pod = newPod();
        Long jobId = startJob(pod);
        requestedLongAgo(jobId);
        newPod().failStaleJobs();          // 다른 Pod 는 이 작업이 살아 있는 줄 모른다
        assertThat(status(jobId)).isEqualTo("FAILED");

        queued.getFirst().run();

        assertThat(aiCalls).hasValue(0);   // 한도에 걸린 바로 그때 Bedrock 을 한 번 더 부르지 않는다
        assertThat(targetCleanedWith).hasValue(FailureCode.TIMEOUT);   // 분석·탭이 "진행 중"에 남지 않는다
        assertThat(status(jobId)).isEqualTo("FAILED");
    }

    @Test
    void 다른_Pod_가_기다리는_작업은_살아_있음이_남아_있어_지우지_않는다() {
        // 배포 중 2대이거나 AI 큐 대기가 길 때 (BE #66). 정리는 Pod 마다 돌고 자기 작업만 안다
        AiJobs podA = newPod();
        Long jobId = startJob(podA);
        requestedLongAgo(jobId);

        podA.failStaleJobs();              // A 가 1분마다 "살아 있음"을 남긴다
        newPod().failStaleJobs();          // B 의 정리

        assertThat(status(jobId)).isEqualTo("IN_PROGRESS");
        queued.getFirst().run();
        assertThat(status(jobId)).isEqualTo("COMPLETED");
    }

    @Test
    void 살아_있음이_오래전에_멈춘_작업은_정리한다() {
        AiJobs podA = newPod();
        Long jobId = startJob(podA);
        podA.failStaleJobs();
        jdbc.sql("UPDATE app.ai_job SET requested_at = now() - interval '2 hours', alive_at = now() - interval '1 hour'"
                + " WHERE id = ?").param(jobId).update();   // 그 뒤 A 가 내려갔다

        newPod().failStaleJobs();

        assertThat(status(jobId)).isEqualTo("FAILED");
    }

    private AiJobs newPod() {
        StaticListableBeanFactory none = new StaticListableBeanFactory();
        return new AiJobs(jobs, transactionManager, queued::add, new AiJobProperties(false, STALE_AFTER, 4, 0),
                new AiJobDrain(), none.getBeanProvider(ObservationRegistry.class),
                none.getBeanProvider(com.nerdnam.infra.ai.AiBacklog.class));
    }

    private Long startJob(AiJobs pod) {
        Long memberId = newMember();
        JobWork<String, String> work = JobWork.<String, String>of(
                        () -> "입력",
                        input -> {
                            aiCalls.incrementAndGet();
                            return "결과";
                        },
                        result -> null)
                .onFailure(targetCleanedWith::set);
        return pod.start(JobRequest.of(memberId, JobType.FIT_ANALYSIS, null, TargetType.JOB_POSTING, memberId), work);
    }

    private void requestedLongAgo(Long jobId) {
        jdbc.sql("UPDATE app.ai_job SET requested_at = now() - interval '1 hour' WHERE id = ?").param(jobId).update();
    }

    private String status(Long jobId) {
        return jdbc.sql("SELECT status FROM app.ai_job WHERE id = ?").param(jobId).query(String.class).single();
    }
}
