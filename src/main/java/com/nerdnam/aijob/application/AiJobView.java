package com.nerdnam.aijob.application;

import com.nerdnam.aijob.domain.AiJob;
import com.nerdnam.aijob.domain.FailureCode;
import com.nerdnam.aijob.domain.JobStatus;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import java.time.Instant;

public record AiJobView(Long id, JobType jobType, String mode, TargetType targetType, Long targetId, String lockScope,
                        JobStatus status, FailureCode failureCode, Long resultId, Instant requestedAt,
                        Instant finishedAt) {

    static AiJobView from(AiJob j) {
        return new AiJobView(j.getId(), j.getJobType(), j.getMode(), j.getTargetType(), j.getTargetId(),
                j.getLockScope(), j.getStatus(), j.getFailureCode(), j.getResultId(), j.getRequestedAt(),
                j.getFinishedAt());
    }
}
