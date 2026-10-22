package com.nerdnam.project.domain;

/** 프로젝트 확정 (처음 확정과 재등록 후 다시 확정이 같다). rag 가 그 프로젝트의 청크를 통째로 다시 만든다 (F3) */
public record ProjectConfirmed(Long projectId, Long memberId) {
}
