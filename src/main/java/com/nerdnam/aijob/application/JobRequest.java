package com.nerdnam.aijob.application;

import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;

/** 누가, 무엇에 대해, 어떤 AI 작업을 요청했나. lockScope 가 같으면 동시에 1건만 */
public record JobRequest(Long memberId, JobType jobType, String mode, TargetType targetType, Long targetId,
                         String lockScope) {

    public static JobRequest of(Long memberId, JobType jobType, String mode, TargetType targetType, Long targetId) {
        return new JobRequest(memberId, jobType, mode, targetType, targetId, "");
    }
}
