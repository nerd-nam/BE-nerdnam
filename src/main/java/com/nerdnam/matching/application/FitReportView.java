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
     * 근거 카드. 웹은 sourceType·reregistering 으로 갈 화면을 정한다 (기획서 v1.25 S08):
     * EPISODE → S05 읽기 모드에서 그 사례를 펼친다(projectId + sourceId = 사례 id),
     * PROJECT_SUMMARY → S05 읽기 모드(projectId = sourceId). 재등록 중이면 둘 다 S06 프로젝트 카드로 간다.
     * CAREER·SKILL → S06 내 경험. 프로젝트가 아닌 근거의 reregistering 은 false다.
     */
    public record EvidenceView(int rank, long chunkId, EvidenceSourceType sourceType, long sourceId, int sourceVersion,
                               Long projectId, boolean reregistering, boolean deleted, String label, String snippet) {
    }
}
