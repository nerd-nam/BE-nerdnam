package com.nerdnam.aijob.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.aijob.domain.AiJobRepository;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.infra.ai.AiBacklog;
import com.nerdnam.support.ApiTestSupport;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 몰렸을 때 새 AI 작업을 <b>만들기 전에</b> 거절한다 (BE #67).
 *
 * <p>큐 경로에서는 큐 대기가 실패가 아니라서, 막지 않으면 사용자는 "진행 중"을 오래 보다가 TIMEOUT 을 받는다.
 * 실행기가 꽉 찬 경우는 지금까지 작업을 만든 뒤 INTERNAL 로 실패시켜 원인이 가려졌다.
 * 둘 다 계약의 AI_UNAVAILABLE(503)이라 웹은 이미 "잠시 뒤 다시 시도"를 띄운다.
 */
class AiJobBusyTest extends ApiTestSupport {

    @Autowired
    AiJobRepository jobs;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void AI_큐가_밀렸으면_작업을_만들지_않고_AI_UNAVAILABLE() {
        Long member = newMember();

        assertThatThrownBy(() -> start(pod(Runnable::run, () -> true), member))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE);
                    assertThat(e.getMessage()).isEqualTo(AiJobs.BUSY_MESSAGE);
                });
        assertThat(jobCount(member)).isZero();
    }

    @Test
    void 작업_스레드와_대기_칸이_모두_찼으면_작업을_만들지_않고_AI_UNAVAILABLE() throws Exception {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.initialize();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch running = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                running.countDown();
                await(release);
            });
            running.await(5, TimeUnit.SECONDS);
            executor.execute(() -> await(release));   // 대기 칸 1개도 찼다
            Long member = newMember();

            assertThatThrownBy(() -> start(pod(executor, AiBacklog.NONE), member))
                    .isInstanceOfSatisfying(BusinessException.class,
                            e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.AI_UNAVAILABLE));
            assertThat(jobCount(member)).isZero();   // 예전에는 작업을 만든 뒤 INTERNAL 로 실패시켰다
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    void 여유가_있으면_그대로_받는다() {
        Long member = newMember();

        Long jobId = start(pod(Runnable::run, () -> false), member);

        assertThat(jdbc.sql("SELECT status FROM app.ai_job WHERE id = ?").param(jobId).query(String.class).single())
                .isEqualTo("COMPLETED");
    }

    private AiJobs pod(TaskExecutor executor, AiBacklog backlog) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("backlog", backlog);
        return new AiJobs(jobs, transactionManager, executor,
                new AiJobProperties(false, Duration.ofMinutes(25), 1, 0), new AiJobDrain(),
                beans.getBeanProvider(ObservationRegistry.class), beans.getBeanProvider(AiBacklog.class));
    }

    private Long start(AiJobs pod, Long member) {
        return pod.start(JobRequest.of(member, JobType.FIT_ANALYSIS, null, TargetType.JOB_POSTING, member),
                JobWork.<String, String>of(() -> "입력", input -> "결과", result -> null));
    }

    private long jobCount(Long member) {
        return jdbc.sql("SELECT count(*) FROM app.ai_job WHERE member_id = ?").param(member).query(Long.class).single();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
