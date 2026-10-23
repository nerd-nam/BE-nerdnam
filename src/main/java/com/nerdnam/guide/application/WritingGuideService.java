package com.nerdnam.guide.application;

import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.aijob.application.JobRequest;
import com.nerdnam.aijob.application.JobWork;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.guide.application.GuideViews.Citation;
import com.nerdnam.guide.application.GuideViews.Overview;
import com.nerdnam.guide.application.GuideViews.TabStatus;
import com.nerdnam.guide.domain.GuideContentPolicy;
import com.nerdnam.guide.domain.CitationSnapshot;
import com.nerdnam.project.application.ProjectEvidenceQuery;
import com.nerdnam.project.domain.ProjectDeleting;
import org.springframework.context.event.EventListener;
import com.nerdnam.guide.domain.GuideTab;
import com.nerdnam.guide.domain.WritingGuide;
import com.nerdnam.guide.domain.WritingGuideRepository;
import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.GuideCall;
import com.nerdnam.infra.ai.MatchCall;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.JobPostingView;
import com.nerdnam.matching.application.FitReportQuery;
import com.nerdnam.matching.application.FitReportView;
import com.nerdnam.matching.application.FitReportView.EvidenceView;
import com.nerdnam.matching.domain.AnalysisStatus;
import com.nerdnam.matching.domain.EvidenceLabel;
import com.nerdnam.matching.domain.EvidenceSourceType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 작성 방향 (S09, BE-14). 탭마다 AI 작업 하나 (lock_scope = 탭) — 탭끼리는 동시에 돌고 같은 탭은 1건만.
 * 생성 횟수 상한은 없다 (재구성과 같은 원칙, 02 문서 8장 S09 4). 생성 시점(리포트 직후 5개 / 탭을 열 때)은 웹이 정한다.
 *
 * <p>AI 서버의 JSON 응답은 작업 스레드가 받은 뒤 저장한다. 받는 동안 DB 트랜잭션을 열지 않는다.
 */
@Service
public class WritingGuideService {

    private final WritingGuideRepository guides;
    private final FitReportQuery reports;
    private final JobPostingService postings;
    private final AiJobs aiJobs;
    private final AiServerClient ai;
    private final JdbcClient jdbc;
    private final ProjectEvidenceQuery projects;
    private final TransactionTemplate readTx;

    public WritingGuideService(WritingGuideRepository guides, FitReportQuery reports, JobPostingService postings,
                               AiJobs aiJobs, AiServerClient ai, JdbcClient jdbc, ProjectEvidenceQuery projects,
                               PlatformTransactionManager transactionManager) {
        this.guides = guides;
        this.reports = reports;
        this.postings = postings;
        this.aiJobs = aiJobs;
        this.ai = ai;
        this.jdbc = jdbc;
        this.projects = projects;
        this.readTx = new TransactionTemplate(transactionManager);
        this.readTx.setReadOnly(true);
    }

    /**
     * 탭 만들기 / "이 탭 다시 만들기". 공고의 가장 최근 분석이 완료돼 있어야 한다.
     * <b>처음 만들 때는 피드백이 없어도 되고, 다시 만들 때는 필수다</b> (C-02 S09 4, 기획서 v1.12 와 같은 규칙).
     * 같은 조건으로 다시 만드는 요청은 GPU 를 같이 먹고 결과도 거의 같다.
     */
    public Long generate(Long memberId, Long postingId, GuideTab tab, String feedback) {
        AtomicReference<Long> guideId = new AtomicReference<>();
        AtomicReference<Set<Long>> allowed = new AtomicReference<>();
        Long analysis = completedReport(memberId, postingId).analysisId();
        JobWork<GuideCall.Request, GuideCall.Result> work = JobWork.<GuideCall.Request, GuideCall.Result>of(
                () -> {
                    FitReportView report = completedReport(memberId, postingId);
                    JobPostingView posting = postings.get(memberId, postingId);
                    int next = guides.maxGenerationNo(report.analysisId(), tab) + 1;
                    if (next > 1 && (feedback == null || feedback.isBlank())) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT, "어떤 방향으로 다시 만들지 알려 주세요.");
                    }
                    WritingGuide guide = guides.saveAndFlush(WritingGuide.start(report.analysisId(), tab, next, feedback));
                    guideId.set(guide.getId());
                    allowed.set(allowedChunkIds(report));
                    List<CitationSnapshot> citations = snapshots(memberId, report, allowed.get());
                    guide.rememberCitations(citations);
                    allowed.set(citations.stream().map(CitationSnapshot::chunkId).collect(java.util.stream.Collectors.toSet()));
                    return request(tab, report, posting, feedback);
                },
                ai::guide,
                result -> {
                    WritingGuide guide = guides.findById(guideId.get()).orElseThrow();
                    guide.complete(GuideContentPolicy.clean(tab, result.content(), result.citedChunkIds(), allowed.get()),
                            result.model(), result.promptVersion());
                    return guide.getId();
                })
                .onFailure(code -> {
                    if (guideId.get() != null) {
                        guides.findById(guideId.get()).ifPresent(WritingGuide::fail);
                    }
                });
        // 작업의 mode·lock_scope 는 탭의 표준 이름(motive 등) — 웹 주소·AI 계약과 같은 값 (기획서 v1.20)
        return aiJobs.start(new JobRequest(memberId, JobType.WRITING_GUIDE, tab.path(), TargetType.FIT_ANALYSIS,
                analysis, tab.path()), work);
    }

    public Overview overview(Long memberId, Long postingId) {
        return readTx.execute(s -> {
            FitReportView report = completedReport(memberId, postingId);
            Map<GuideTab, WritingGuide> latest = new LinkedHashMap<>();
            guides.findLatestPerTab(report.analysisId()).forEach(g -> latest.put(g.getTab(), g));
            List<TabStatus> tabs = new ArrayList<>();
            for (GuideTab tab : GuideTab.values()) {
                WritingGuide g = latest.get(tab);
                tabs.add(new TabStatus(tab, tab.path(), g == null ? null : g.getStatus(),
                        g == null ? null : g.getGenerationNo(), running(report.analysisId(), tab)));
            }
            return new Overview(postingId, report.companyName(), report.positionName(), report.analysisId(), tabs);
        });
    }

    public GuideViews.Guide get(Long memberId, Long postingId, GuideTab tab) {
        return readTx.execute(s -> {
            FitReportView report = completedReport(memberId, postingId);
            WritingGuide g = guides.findFirstByFitAnalysisIdAndTabOrderByGenerationNoDesc(report.analysisId(), tab)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "아직 만들지 않은 탭이에요."));
            return new GuideViews.Guide(g.getId(), tab, tab.path(), g.getGenerationNo(), g.getStatus(), g.getContent(),
                    g.getFeedback(), citations(memberId, report, g), g.getCreatedAt(), g.getCompletedAt(),
                    running(report.analysisId(), tab));
        });
    }

    // ------------------------------------------------------------------ 내부

    private FitReportView completedReport(Long memberId, Long postingId) {
        FitReportView report = reports.latest(memberId, postingId);
        if (report.status() != AnalysisStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "적합도 분석이 끝난 뒤에 작성 방향을 만들 수 있어요.");
        }
        return report;
    }

    private Long running(Long analysisId, GuideTab tab) {
        return aiJobs.runningJobId(TargetType.FIT_ANALYSIS, analysisId, tab.path()).orElse(null);
    }

    /** /guide 입력: 판정 결과 + 근거 원문 + JD 컨텍스트 + 저장된 갭 가이드 (S08·S09 조언이 어긋나지 않게, ADR-0003) */
    private static GuideCall.Request request(GuideTab tab, FitReportView report, JobPostingView posting, String feedback) {
        List<GuideCall.Requirement> requirements = report.matches().stream()
                .map(m -> new GuideCall.Requirement(m.requirementId(), m.type().toLowerCase(), m.text(),
                        m.verdict().name().toLowerCase(), m.reason(),
                        m.evidences().stream()
                                .filter(e -> !e.deleted())
                                .map(e -> new MatchCall.Candidate(e.chunkId(), e.sourceType().name().toLowerCase(), e.snippet()))
                                .toList(),
                        m.gapGuide() == null ? null : new GuideCall.GapGuide(m.gapGuide().advice(), m.gapGuide().avoid())))
                .toList();
        return new GuideCall.Request(tab.aiName(), posting.companyName(), posting.positionName(), posting.companyIntro(),
                posting.tasks(), requirements, feedback);
    }

    /** 인용해도 되는 청크: 이 분석의 근거 + 갭 가이드가 인용한 인접 경험 */
    private static Set<Long> allowedChunkIds(FitReportView report) {
        Set<Long> allowed = new HashSet<>();
        report.matches().forEach(m -> {
            m.evidences().stream().filter(e -> !e.deleted()).forEach(e -> allowed.add(e.chunkId()));
            if (m.gapGuide() != null) {
                allowed.addAll(m.gapGuide().citedChunkIds());
            }
        });
        return allowed;
    }

    /** 기존 작성 방향에도 삭제 전에 인용 이름을 남긴다. 원본과 같은 트랜잭션이라 함께 취소된다. */
    @EventListener
    public void beforeDelete(ProjectDeleting event) {
        jdbc.sql("""
                SELECT g.id FROM app.writing_guide g JOIN app.fit_analysis a ON a.id = g.fit_analysis_id
                WHERE a.member_id = :member AND g.citation_snapshots IS NULL AND g.content IS NOT NULL
                """).param("member", event.memberId()).query(Long.class).list().forEach(id -> {
                    WritingGuide guide = guides.findById(id).orElseThrow();
                    guide.rememberCitations(snapshots(event.memberId(), reports.get(event.memberId(), guide.getFitAnalysisId()),
                            citedIds(guide)));
                });
    }

    /** "이 방향에 쓰인 근거": 저장한 이름에 현재 원본의 삭제·재등록 상태를 붙인다. */
    private List<Citation> citations(Long memberId, FitReportView report, WritingGuide guide) {
        Set<Long> ids = citedIds(guide);
        List<CitationSnapshot> saved = guide.getCitationSnapshots() == null
                ? snapshots(memberId, report, ids) : guide.getCitationSnapshots();
        var references = projects.projects(memberId, saved.stream().map(CitationSnapshot::projectId)
                .filter(java.util.Objects::nonNull).distinct().toList());
        return saved.stream().filter(c -> ids.contains(c.chunkId())).map(c -> {
            var project = c.projectId() == null ? null : references.get(c.projectId());
            boolean projectSource = c.sourceType() == EvidenceSourceType.EPISODE
                    || c.sourceType() == EvidenceSourceType.PROJECT_SUMMARY;
            boolean deleted = projectSource && project == null;
            return new Citation(c.chunkId(), c.sourceType(), c.sourceId(), deleted ? null : c.projectId(),
                    c.label(), c.requirementCount(), deleted, project != null && project.reregistering());
        }).toList();
    }

    private static Set<Long> citedIds(WritingGuide guide) {
        if (guide.getContent() == null || !(guide.getContent().get("cited_chunk_ids") instanceof List<?> ids)) {
            return Set.of();
        }
        return ids.stream().map(o -> ((Number) o).longValue())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private List<CitationSnapshot> snapshots(Long memberId, FitReportView report, Set<Long> ids) {
        Map<Long, EvidenceView> evidence = new LinkedHashMap<>();
        Map<Long, Integer> counts = new LinkedHashMap<>();
        report.matches().forEach(m -> m.evidences().stream().map(EvidenceView::chunkId).distinct().forEach(id ->
                counts.merge(id, 1, Integer::sum)));
        report.matches().forEach(m -> m.evidences().forEach(e -> evidence.putIfAbsent(e.chunkId(), e)));
        List<CitationSnapshot> result = new ArrayList<>();
        for (long id : ids) {
            EvidenceView e = evidence.get(id);
            if (e != null) {
                if (!e.deleted()) {
                    result.add(new CitationSnapshot(id, e.sourceType(), e.sourceId(), e.projectId(), e.label(),
                            counts.getOrDefault(id, 0)));
                }
            } else {
                chunk(memberId, id).ifPresent(result::add);
            }
        }
        return result;
    }

    private java.util.Optional<CitationSnapshot> chunk(Long memberId, long chunkId) {
        return jdbc.sql("SELECT chunk_type, source_id, project_id, content FROM rag.chunk WHERE id = :id AND member_id = :m")
                .param("id", chunkId).param("m", memberId)
                .query((rs, n) -> {
                    EvidenceSourceType type = EvidenceSourceType.valueOf(rs.getString(1));
                    return new CitationSnapshot(chunkId, type, rs.getLong(2), (Long) rs.getObject(3),
                            EvidenceLabel.of(type, rs.getString(4)), 0);
                }).optional();
    }
}
