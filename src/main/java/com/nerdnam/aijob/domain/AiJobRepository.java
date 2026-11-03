package com.nerdnam.aijob.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AiJobRepository extends Repository<AiJob, Long> {

    AiJob saveAndFlush(AiJob job);

    Optional<AiJob> findById(Long id);

    Optional<AiJob> findByIdAndMemberId(Long id, Long memberId);

    @Query("""
            select j.id from AiJob j
            where j.targetType = :targetType and j.targetId = :targetId and j.lockScope = :lockScope
              and j.status = com.nerdnam.aijob.domain.JobStatus.IN_PROGRESS
            """)
    Optional<Long> findRunningId(@Param("targetType") TargetType targetType, @Param("targetId") Long targetId,
                                 @Param("lockScope") String lockScope);

    /**
     * 이 Pod 가 들고 있는 작업에 "살아 있음"을 남긴다 (BE #66). 다른 Pod 의 정리가 이 작업을 지우지 않게 한다.
     */
    @Modifying
    @Query("""
            update AiJob j set j.aliveAt = :now
            where j.id in :alive and j.status = com.nerdnam.aijob.domain.JobStatus.IN_PROGRESS
            """)
    int markAlive(@Param("alive") Collection<Long> alive, @Param("now") Instant now);

    /**
     * 서버가 꺼지는 등으로 끝나지 못한 작업을 실패로 정리한다. 시간은 마지막으로 살아 있던 시각부터 잰다
     * (없으면 접수 시각).
     *
     * @param alive 이 Pod 가 아직 들고 있는 작업(큐에서 기다리거나 도는 중). 시간이 지났어도 살아 있으므로 빼야 한다
     */
    @Modifying
    @Query("""
            update AiJob j set j.status = com.nerdnam.aijob.domain.JobStatus.FAILED,
                               j.failureCode = com.nerdnam.aijob.domain.FailureCode.TIMEOUT,
                               j.finishedAt = :now
            where j.status = com.nerdnam.aijob.domain.JobStatus.IN_PROGRESS
              and coalesce(j.aliveAt, j.requestedAt) < :before
              and j.id not in :alive
            """)
    int failStale(@Param("before") Instant before, @Param("now") Instant now, @Param("alive") Collection<Long> alive);
}
