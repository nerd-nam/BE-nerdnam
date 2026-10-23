package com.nerdnam.aijob.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 작업 1건 (재구성·적합도 분석·작성 방향 탭). 웹은 작업 id 로 상태를 조회(폴링)한다.
 * 진행 중 → 완료(결과 대상 id) 또는 실패(이유). 끝난 작업은 바뀌지 않는다.
 */
@Entity
@Table(name = "ai_job")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30, updatable = false)
    private JobType jobType;

    /** RESTRUCTURE: SPLIT/RESPLIT/REVISION/ANSWER, WRITING_GUIDE: 탭 이름 */
    @Column(name = "mode", length = 30, updatable = false)
    private String mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private TargetType targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    /** 같은 대상 안에서 따로 돌아도 되는 단위 (작성 방향은 탭 이름, 나머지는 빈 값) */
    @Column(name = "lock_scope", nullable = false, length = 30, updatable = false)
    private String lockScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private JobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 30)
    private FailureCode failureCode;

    /** 완료 후 웹이 다시 조회할 대상 (fit_analysis id, writing_guide id 등) */
    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public static AiJob start(Long memberId, JobType jobType, String mode, TargetType targetType, Long targetId,
                              String lockScope) {
        AiJob job = new AiJob();
        job.memberId = Objects.requireNonNull(memberId);
        job.jobType = Objects.requireNonNull(jobType);
        job.mode = mode;
        job.targetType = Objects.requireNonNull(targetType);
        job.targetId = Objects.requireNonNull(targetId);
        job.lockScope = lockScope == null ? "" : lockScope;
        job.status = JobStatus.IN_PROGRESS;
        job.requestedAt = Instant.now();
        return job;
    }

    public void complete(Long resultId) {
        if (status != JobStatus.IN_PROGRESS) {
            return;   // 오래 멈춘 작업 정리가 먼저 실패 처리했으면 그대로 둔다
        }
        this.status = JobStatus.COMPLETED;
        this.resultId = resultId;
        this.finishedAt = Instant.now();
    }

    public void fail(FailureCode code) {
        if (status != JobStatus.IN_PROGRESS) {
            return;
        }
        this.status = JobStatus.FAILED;
        this.failureCode = Objects.requireNonNull(code);
        this.finishedAt = Instant.now();
    }

    public boolean isInProgress() {
        return status == JobStatus.IN_PROGRESS;
    }
}
