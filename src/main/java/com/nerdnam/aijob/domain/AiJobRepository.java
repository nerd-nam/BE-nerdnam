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
     * 서버가 꺼지는 등으로 끝나지 못한 작업을 실패로 정리한다.
     *
     * @param alive 이 Pod 가 아직 들고 있는 작업(큐에서 기다리거나 도는 중). 시간이 지났어도 살아 있으므로 빼야 한다
     */
    @Modifying
    @Query("""
            update AiJob j set j.status = com.nerdnam.aijob.domain.JobStatus.FAILED,
                               j.failureCode = com.nerdnam.aijob.domain.FailureCode.TIMEOUT,
                               j.finishedAt = :now
            where j.status = com.nerdnam.aijob.domain.JobStatus.IN_PROGRESS and j.requestedAt < :before
              and j.id not in :alive
            """)
    int failStale(@Param("before") Instant before, @Param("now") Instant now, @Param("alive") Collection<Long> alive);
}
