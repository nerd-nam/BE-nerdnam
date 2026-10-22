package com.nerdnam.aijob.domain;

/** 작업 대상. 같은 대상(+ lock_scope)에는 진행 중인 작업이 1건만 (uk_ai_job_running) */
public enum TargetType {
    PROJECT,
    JOB_POSTING,
    FIT_ANALYSIS
}
