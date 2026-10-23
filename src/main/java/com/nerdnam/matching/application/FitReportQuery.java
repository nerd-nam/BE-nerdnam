package com.nerdnam.matching.application;

import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.JobPostingView;
import com.nerdnam.matching.application.FitReportView.EvidenceView;
import com.nerdnam.matching.application.FitReportView.MatchView;
import com.nerdnam.matching.domain.AnalysisStatus;
import com.nerdnam.matching.domain.EvidenceSourceType;
import com.nerdnam.matching.domain.FitAnalysis;
import com.nerdnam.matching.domain.FitAnalysisRepository;
import com.nerdnam.matching.domain.FitReport;
import com.nerdnam.matching.domain.MatchEvidence;
import com.nerdnam.project.application.ProjectEvidenceQuery;
import com.nerdnam.project.application.ProjectEvidenceQuery.Reference;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 적합도 리포트 조회 (BE-13) */
@Service
@Transactional(readOnly = true)
public class FitReportQuery {

    private final FitAnalysisRepository analyses;
    private final JobPostingService postings;
    private final AiJobs aiJobs;
    private final ProjectEvidenceQuery projects;

    public FitReportQuery(FitAnalysisRepository analyses, JobPostingService postings, AiJobs aiJobs, ProjectEvidenceQuery projects) {
        this.analyses = analyses;
        this.postings = postings;
        this.aiJobs = aiJobs;
        this.projects = projects;
    }

    /** 공고의 가장 최근 분석 (S08 을 열 때). 분석한 적이 없으면 404 */
    public FitReportView latest(Long memberId, Long postingId) {
        JobPostingView posting = postings.get(memberId, postingId);   // 소유권 확인 겸
        FitAnalysis analysis = analyses.findFirstByJobPostingIdAndMemberIdOrderByRequestedAtDescIdDesc(postingId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "아직 분석한 적이 없는 공고예요."));
        return view(analysis, posting);
    }

    /**
     * 분석 기록 (S08 "이전 분석 보기", 기획서 v1.25). 공고를 고쳐 다시 분석하면 덮어쓰지 않고 새로 쌓으므로,
     * 웹이 목록에서 골라 {@code GET /api/fit-analyses/{id}} 로 그 분석을 본다. 최신순.
     */
    public List<AnalysisSummary> history(Long memberId, Long postingId) {
        postings.get(memberId, postingId);   // 소유권 확인
        return analyses.findAllByJobPostingIdAndMemberIdOrderByRequestedAtDescIdDesc(postingId, memberId).stream()
                .map(a -> new AnalysisSummary(a.getId(), a.getStatus(), lower(a.getFailureCode()), a.getRequestedAt(),
                        a.getCompletedAt()))
                .toList();
    }

    /** 분석 하나의 요약 (목록용). 내용은 리포트 조회로 받는다 */
    public record AnalysisSummary(Long analysisId, AnalysisStatus status, @Nullable String failureCode,
                                  Instant requestedAt, @Nullable Instant completedAt) {
    }

    public FitReportView get(Long memberId, Long analysisId) {
        FitAnalysis analysis = analyses.findByIdAndMemberId(analysisId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "분석 결과를 찾을 수 없어요."));
        return view(analysis, postings.get(memberId, analysis.getJobPostingId()));
    }

    private FitReportView view(FitAnalysis a, JobPostingView posting) {
        Long running = aiJobs.runningJobId(TargetType.JOB_POSTING, posting.id(), "").orElse(null);
        if (a.getStatus() != AnalysisStatus.COMPLETED) {
            return new FitReportView(a.getId(), posting.id(), posting.companyName(), posting.positionName(), a.getStatus(),
                    lower(a.getFailureCode()), a.getRequestedAt(), a.getCompletedAt(), null, null, null, List.of(), running);
        }
        FitReport report = a.report();
        Map<Long, Reference> episodes = projects.episodes(a.getMemberId(), sourceIds(a, EvidenceSourceType.EPISODE));
        Map<Long, Reference> summaries = projects.projects(a.getMemberId(), sourceIds(a, EvidenceSourceType.PROJECT_SUMMARY));
        List<MatchView> matches = a.getMatches().stream()
                .map(m -> new MatchView(m.getId(), m.getRequirementId(), lower(m.getRequirementType()), m.getRequirementText(),
                        m.getSkills(), m.getMinYears(), m.getRuleVerdict(), m.getLlmVerdict(), m.getFinalVerdict(),
                        m.getReason(), m.getEvidences().stream().map(e -> evidence(e, episodes, summaries)).toList(),
                        m.gapGuide()))
                .toList();
        return new FitReportView(a.getId(), posting.id(), posting.companyName(), posting.positionName(), a.getStatus(),
                lower(a.getFailureCode()), a.getRequestedAt(), a.getCompletedAt(), report.required(), report.preferred(),
                report.diagnosis(), matches, running);
    }

    /** 응답 JSON 의 값은 소문자 (D-10). 요건 유형은 문자열로 저장돼 있어 여기서 낮춘다 */
    private static String lower(String value) {
        return value == null ? null : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static EvidenceView evidence(MatchEvidence e, Map<Long, Reference> episodes, Map<Long, Reference> summaries) {
        Reference project = switch (e.getSourceType()) {
            case EPISODE -> episodes.get(e.getSourceId());
            case PROJECT_SUMMARY -> summaries.get(e.getSourceId());
            default -> null;
        };
        boolean deleted = e.isSourceDeleted() || (project == null
                && (e.getSourceType() == EvidenceSourceType.EPISODE || e.getSourceType() == EvidenceSourceType.PROJECT_SUMMARY));
        return new EvidenceView(e.getRankNo(), e.getChunkId(), e.getSourceType(), e.getSourceId(), e.getSourceVersion(),
                project == null ? null : project.projectId(), project != null && project.reregistering(), deleted, e.label(), deleted ? null : e.getSnippet());
    }

    private static List<Long> sourceIds(FitAnalysis a, EvidenceSourceType type) {
        return a.getMatches().stream()
                .flatMap(m -> m.getEvidences().stream())
                .filter(e -> e.getSourceType() == type)
                .map(MatchEvidence::getSourceId)
                .distinct()
                .toList();
    }
}
