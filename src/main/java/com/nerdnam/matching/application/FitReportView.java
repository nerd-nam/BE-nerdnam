package com.nerdnam.matching.application;

import com.nerdnam.matching.domain.AnalysisStatus;
import com.nerdnam.matching.domain.EvidenceSourceType;
import com.nerdnam.matching.domain.FitReport;
import com.nerdnam.matching.domain.GapGuide;
import com.nerdnam.matching.domain.Verdict;
import java.time.Instant;
import java.util.List;

/** 적합도 리포트 (S08). 분석 중·실패면 counts·diagnosis·matches 가 비어 있다 */
public record FitReportView(Long analysisId, Long postingId, String companyName, String positionName,
                            AnalysisStatus status, String failureCode, Instant requestedAt, Instant completedAt,
                            FitReport.Count required, FitReport.Count preferred, String diagnosis,
                            List<MatchView> matches, Long runningJobId) {

    /** @param verdict 최종 판정 (규칙·LLM 중 낮은 쪽) */
    public record MatchView(Long id, Long requirementId, String type, String text, List<String> skills,
                            Integer minYears, Verdict ruleVerdict, Verdict llmVerdict, Verdict verdict, String reason,
                            List<EvidenceView> evidences, GapGuide gapGuide) {
    }

    /**
     * 근거 카드. 웹은 sourceType 으로 갈 화면을 정한다: EPISODE·PROJECT_SUMMARY → S05/S06(projectId),
     * CAREER → S03(sourceId), SKILL → S02(sourceId = 기술스택 항목 id).
     */
    public record EvidenceView(int rank, long chunkId, EvidenceSourceType sourceType, long sourceId, int sourceVersion,
                               Long projectId, String label, String snippet) {
    }
}
