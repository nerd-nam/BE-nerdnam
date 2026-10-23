package com.nerdnam.guide.application;

import com.nerdnam.guide.domain.GuideStatus;
import com.nerdnam.guide.domain.GuideTab;
import com.nerdnam.matching.domain.EvidenceSourceType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class GuideViews {

    private GuideViews() {
    }

    /** S09 을 열 때: 탭마다 최근 생성 상태 (탭 5개를 한 번에) */
    public record Overview(Long postingId, String companyName, String positionName, Long analysisId, List<TabStatus> tabs) {
    }

    /** @param status 아직 만든 적이 없으면 null */
    public record TabStatus(GuideTab tab, String path, GuideStatus status, Integer generationNo, Long runningJobId) {
    }

    /** 탭 하나의 최근 결과 + "이 방향에 쓰인 근거" */
    public record Guide(Long id, GuideTab tab, String path, int generationNo, GuideStatus status,
                        Map<String, Object> content, String feedback, List<Citation> citations, Instant createdAt,
                        Instant completedAt, Long runningJobId) {
    }

    /** @param requirementCount 이 근거가 뒷받침한 요건 수 */
    public record Citation(long chunkId, EvidenceSourceType sourceType, Long sourceId, Long projectId, String label,
                           int requirementCount) {
    }
}
