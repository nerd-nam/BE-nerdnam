package com.nerdnam.guide.domain;

import com.nerdnam.matching.domain.EvidenceSourceType;

/** 원문이 삭제돼도 남길 인용 이름. 원문은 복사하지 않는다. */
public record CitationSnapshot(long chunkId, EvidenceSourceType sourceType, Long sourceId, Long projectId,
                               String label, int requirementCount) {
}
