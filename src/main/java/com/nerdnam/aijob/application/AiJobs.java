package com.nerdnam.aijob.application;

import com.nerdnam.aijob.domain.AiJob;
import com.nerdnam.aijob.domain.AiJobRepository;
import com.nerdnam.aijob.domain.FailureCode;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.annotation.PreDestroy;
import com.nerdnam.infra.ai.AiCallContext;
import com.nerdnam.infra.ai.AiServerUnavailableException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 작업 — aijob 모듈의 공개 입구. 재구성·적합도 분석·작성 방향이 모두 이 흐름을 쓴다.
 *
 * <pre>
 * 요청 스레드: [트랜잭션 ① prepare + 작업 행 저장] → 작업 id 바로 응답 (202)
 * 작업 스레드: [AI 호출, 트랜잭션 밖] → [트랜잭션 ② apply + 작업 완료]
 *                                  실패하면 [작업 실패 + onFailure]
 * </pre>
 * 같은 대상(+ lockScope)에 진행 중인 작업이 있으면 DB 부분 유니크 인덱스(uk_ai_job_running)가 막고 409 를 준다.
 * 서버가 여러 대여도 맞다.
 *
 * <p>이 클래스를 부르는 서비스 메서드에는 {@code @Transactional}을 붙이지 않는다. 트랜잭션 경계는 여기서 정한다.
 */
@Service
public class AiJobs {

    private static final Logger log = LoggerFactory.getLogger(AiJobs.class);

    private final AiJobRepository jobs;
    private final TransactionTemplate tx;
    private final TaskExecutor executor;
    private final AiJobProperties properties;
    private final AiJobDrain drain;
    private final ObservationRegistry observations;
    /** 이 Pod 가 접수해 아직 끝내지 못한 작업. 종료 때 실패로 정리한다 (BE #45) */
    private final Map<Long, JobWork<?, ?>> inFlight = new ConcurrentHashMap<>();

    public AiJobs(AiJobRepository jobs, PlatformTransactionManager transactionManager,
                  @Qualifier("aiJobExecutor") TaskExecutor executor, AiJobProperties properties,
                  AiJobDrain drain, ObjectProvider<ObservationRegistry> observations) {
        this.jobs = jobs;
        this.tx = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.properties = properties;
        this.drain = drain;
        this.observations = observations.getIfAvailable(() -> ObservationRegistry.NOOP);
        drain.inFlightSource(inFlight::size);
    }

    /** 작업을 접수하고 별도 스레드에서 실행한다. 돌려주는 값은 작업 id */
    public <T, R> Long start(JobRequest request, JobWork<T, R> work) {
        // 배포 준비로 접수를 멈춘 상태면 새 작업을 받지 않는다 (BE #45). 계약에 있는 코드라
        // 웹은 이미 "잠시 뒤 다시 시도해 주세요"를 띄운다 — 사용자가 다시 누르면 새 Pod 가 받는다
        if (drain.draining()) {
            throw new BusinessException(ErrorCode.AI_UNAVAILABLE, ErrorCode.AI_UNAVAILABLE.defaultMessage());
        }
        Accepted<T> accepted = accept(request, work);
        inFlight.put(accepted.jobId(), work);
        Runnable task = () -> run(accepted.jobId(), request, accepted.input(), work);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // 바깥 트랜잭션이 있으면 작업 행이 커밋된 뒤에 실행해야 작업 스레드가 그 행을 볼 수 있다
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(accepted.jobId(), task, work);
                }
            });
        } else {
            submit(accepted.jobId(), task, work);
        }
        return accepted.jobId();
    }

    public AiJobView get(Long memberId, Long jobId) {
        return tx.execute(s -> jobs.findByIdAndMemberId(jobId, memberId).map(AiJobView::from)
                .orElseThrow(BusinessException::notFound));
    }

    /** 화면 조회 응답의 "진행 중인 작업 id" (C-02 공통 3) */
    public Optional<Long> runningJobId(TargetType targetType, Long targetId, String lockScope) {
        return jobs.findRunningId(targetType, targetId, lockScope == null ? "" : lockScope);
    }

    /** 서버 재시작 등으로 끝나지 못한 작업을 정리한다 (1분마다) */
    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void failStaleJobs() {
        Instant now = Instant.now();
        Integer count = tx.execute(s -> jobs.failStale(now.minus(properties.staleAfter()), now));
        if (count != null && count > 0) {
            log.warn("Marked {} stale AI jobs as failed (TIMEOUT)", count);
        }
    }

    // ------------------------------------------------------------------ 내부

    private <T> Accepted<T> accept(JobRequest request, JobWork<T, ?> work) {
        try {
            return tx.execute(s -> {
                T input = work.prepare().get();
                AiJob job = jobs.saveAndFlush(AiJob.start(request.memberId(), request.jobType(), request.mode(),
                        request.targetType(), request.targetId(), request.lockScope()));
                return new Accepted<>(job.getId(), input);
            });
        } catch (DataIntegrityViolationException e) {
            Long running = runningJobId(request.targetType(), request.targetId(), request.lockScope()).orElse(null);
            throw new BusinessException(ErrorCode.AI_JOB_RUNNING, ErrorCode.AI_JOB_RUNNING.defaultMessage(),
                    running == null ? Map.of() : Map.of("runningJobId", running));
        }
    }

    private void submit(Long jobId, Runnable task, JobWork<?, ?> work) {
        try {
            executor.execute(task);
        } catch (RuntimeException e) {   // 스레드 풀이 꽉 참 등
            log.error("Could not submit AI job {}", jobId, e);
            fail(jobId, FailureCode.INTERNAL, work);
        }
    }

    /**
     * 작업 스레드에서 도는 부분은 별도 span({@code nerdnam.ai.job})으로 만든다 (BE #51). 접수 요청의 span 은 바로
     * 끝나고 이 span 이 그 자식으로 이어진다(실행기가 trace 를 넘긴다). 작업 id 는 span 속성으로만 넣는다 —
     * 지표 label 로 쓰면 시계열이 무한히 늘어나므로 high cardinality 로 둔다.
     */
    private <T, R> void run(Long jobId, JobRequest request, T input, JobWork<T, R> work) {
        Observation.createNotStarted("nerdnam.ai.job", observations)
                .contextualName("ai-job " + request.jobType().name().toLowerCase(java.util.Locale.ROOT))
                .lowCardinalityKeyValue("job.type", request.jobType().name().toLowerCase(java.util.Locale.ROOT))
                .highCardinalityKeyValue("job.id", String.valueOf(jobId))
                .observe(() -> runObserved(jobId, request, input, work));
    }

    private <T, R> void runObserved(Long jobId, JobRequest request, T input, JobWork<T, R> work) {
        AiCallContext context = new AiCallContext(request.memberId(), request.targetType().name(), request.targetId());
        try {
            R result = AiCallContext.with(context, () -> work.call().apply(input));
            tx.executeWithoutResult(s -> {
                Long resultId = work.apply().apply(result);
                jobs.findById(jobId).ifPresent(job -> job.complete(resultId));
            });
        } catch (AiServerUnavailableException e) {
            log.warn("AI job {} failed: {} ({})", jobId, e.reason(), e.operation());
            fail(jobId, FailureCode.valueOf(e.reason().name()), work);
        } catch (BusinessException e) {
            // 반영하려는데 규칙에 걸림 (예: 그사이 프로젝트가 확정됨, AI 가 사례를 하나도 못 만듦)
            log.warn("AI job {} could not be applied: {}", jobId, e.getMessage());
            fail(jobId, FailureCode.AI_ERROR, work);
        } catch (RuntimeException e) {
            log.error("AI job {} failed unexpectedly", jobId, e);
            fail(jobId, FailureCode.INTERNAL, work);
        } finally {
            inFlight.remove(jobId);   // 끝났으니 종료 때 정리할 대상이 아니다 (BE #45)
        }
    }

    private void fail(Long jobId, FailureCode code, JobWork<?, ?> work) {
        try {
            tx.executeWithoutResult(s -> {
                jobs.findById(jobId).ifPresent(job -> job.fail(code));
                work.onFailure().accept(code);
            });
        } catch (RuntimeException e) {
            log.error("Could not mark AI job {} as failed", jobId, e);
        }
    }

    /**
     * 종료 때 끝내지 못한 작업을 실패로 정리한다 (BE #45). 큐에만 있던 작업까지 함께 정리된다 —
     * 큐가 메모리에 있어 Pod 가 내려가면 아무도 실행하지 않기 때문이다.
     *
     * <p>이렇게 해야 사용자가 "만드는 중"에 갇히지 않고 바로 "다시 시도"를 본다.
     * 그러지 않으면 오래 멈춘 작업 정리(기본 {@code stale-after} 20분)가 돌 때까지 기다려야 한다.
     * 배포 전에 {@code /actuator/aijob} 으로 접수를 멈추고 0 이 되기를 기다리면 여기서 할 일이 없다.
     */
    @PreDestroy
    void failUnfinishedOnShutdown() {
        if (inFlight.isEmpty()) {
            return;
        }
        log.warn("Shutting down with {} unfinished AI job(s); marking them failed so users can retry: {}",
                inFlight.size(), inFlight.keySet());
        inFlight.forEach((jobId, work) -> fail(jobId, FailureCode.INTERNAL, work));
        inFlight.clear();
    }

    private record Accepted<T>(Long jobId, T input) {
    }
}
