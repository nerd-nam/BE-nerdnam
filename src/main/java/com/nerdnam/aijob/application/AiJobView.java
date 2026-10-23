package com.nerdnam.aijob.application;

import com.nerdnam.aijob.domain.AiJob;
import com.nerdnam.aijob.domain.FailureCode;
import com.nerdnam.aijob.domain.JobStatus;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import java.time.Instant;
import java.util.Locale;

/** 응답 JSON 의 값은 소문자다 (D-10). mode·lockScope 는 저장 값이 대문자일 수 있어 여기서 낮춘다 */
public record AiJobView(Long id, JobType jobType, String mode, TargetType targetType, Long targetId, String lockScope,
                        JobStatus status, FailureCode failureCode, Long resultId, Instant requestedAt,
                        Instant finishedAt) {

    static AiJobView from(AiJob j) {
        return new AiJobView(j.getId(), j.getJobType(), lower(j.getMode()), j.getTargetType(), j.getTargetId(),
                lower(j.getLockScope()), j.getStatus(), j.getFailureCode(), j.getResultId(), j.getRequestedAt(),
                j.getFinishedAt());
    }

    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
