package com.nerdnam.rag.application;

import java.time.YearMonth;
import java.util.List;

/**
 * 저장할 청크 하나 (아직 벡터 없음). 원본(기술·경력·프로젝트·사례)에서 언제든 다시 만들 수 있다.
 *
 * @param sourceVersion 사례는 버전 번호, 경력·프로젝트는 엔티티 version, 기술은 1
 * @param projectId     프로젝트 요약·사례 청크의 프로젝트 (재확정 때 통째로 교체)
 * @param months        기간 개월 수 (경력·프로젝트). 연차 판정의 메타데이터
 */
public record ChunkDraft(ChunkType type, long sourceId, int sourceVersion, Long projectId, String content,
                         List<String> skills, YearMonth periodStart, YearMonth periodEnd, Integer months,
                         boolean hasMetric) {

    public ChunkDraft {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }
}
