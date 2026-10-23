package com.nerdnam.matching.domain;

/** 하이브리드 검색으로 찾은 근거 후보 청크 (이 회원의 것만) */
public record Candidate(long chunkId, EvidenceSourceType sourceType, long sourceId, int sourceVersion, String content,
                        double score) {
}
