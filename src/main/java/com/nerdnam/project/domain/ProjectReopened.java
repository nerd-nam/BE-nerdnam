package com.nerdnam.project.domain;

/** 재등록 시작. 기록용 — 청크는 다시 확정할 때까지 그대로 검색에 쓴다 (Q4) */
public record ProjectReopened(Long projectId, Long memberId) {
}
