package com.nerdnam.project.domain;

/** 프로젝트 상태 (기획서 F2): 초안 → 검토 중 → 확정. 재등록하면 확정 → 초안 */
public enum ProjectStatus {
    DRAFT,
    REVIEW,
    CONFIRMED
}
