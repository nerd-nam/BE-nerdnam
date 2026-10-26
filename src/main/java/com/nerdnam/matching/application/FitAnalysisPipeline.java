package com.nerdnam.matching.application;

import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.EmbedItem;
import com.nerdnam.infra.ai.EmbedPurpose;
import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.infra.ai.GapGuideCall;
import com.nerdnam.infra.ai.MatchCall;
import com.nerdnam.infra.ai.RequirementExtraction;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.PostingForAnalysis;
import com.nerdnam.matching.domain.Candidate;
import com.nerdnam.matching.domain.EvidenceSourceType;
import com.nerdnam.matching.domain.ExperienceYearsCalculator;
import com.nerdnam.matching.domain.ExperienceYearsCalculator.CareerPeriod;
import com.nerdnam.matching.domain.GapGuide;
import com.nerdnam.matching.domain.RequirementMatch.Judged;
import com.nerdnam.matching.domain.RequirementSnapshot;
import com.nerdnam.matching.domain.Verdict;
import com.nerdnam.profile.application.CareerService;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.profile.application.UserSkillView;
import com.nerdnam.rag.application.ChunkSearch;
import com.nerdnam.skill.application.SkillCatalog;
import com.nerdnam.skill.application.SkillSummary;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 적합도 분석 파이프라인 (기획서 5.4 검색·생성, BE-12). AI 작업 스레드에서 트랜잭션 없이 돈다.
 * DB 쓰기(추출 결과 저장)는 각 서비스의 짧은 트랜잭션으로 하고, AI 호출 중에는 DB 연결을 잡지 않는다.
 *
 * <pre>
 * 요건 추출(AI) → 기술명 정규화·저장 → 요건 임베딩(AI) → 하이브리드 검색(요건별 후보 20)
 *   → 연차 규칙 판정 → 판정·리랭킹(AI /match) → 갭 대응 가이드(AI /gap-guide, 실패해도 계속)
 * </pre>
 * 요건은 하나씩이 아니라 묶어서 호출한다 (기획서 5.1).
 */
@Component
class FitAnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(FitAnalysisPipeline.class);
    static final int CANDIDATES = 20;
    static final int ADJACENT = 5;

    private final AiServerClient ai;
    private final JobPostingService postings;
    private final ChunkSearch search;
    private final CareerService careers;
    private final UserSkillService userSkills;
    private final SkillCatalog skillCatalog;

    FitAnalysisPipeline(AiServerClient ai, JobPostingService postings, ChunkSearch search, CareerService careers,
                        UserSkillService userSkills, SkillCatalog skillCatalog) {
        this.ai = ai;
        this.postings = postings;
        this.search = search;
        this.careers = careers;
        this.userSkills = userSkills;
        this.skillCatalog = skillCatalog;
    }

    record Output(List<Judged> judged, String aiModel, String promptVersion) {
    }

    Output run(Long memberId, PostingForAnalysis posting) {
        // 1. 요건 정보 추출 (AI) → 2. 정규화하고 공고에 저장
        RequirementExtraction.Result extraction = ai.extractRequirements(new RequirementExtraction.Request(
                posting.requirements().stream()
                        .map(l -> new RequirementExtraction.Line(l.requirementId(), l.type().toLowerCase(), l.text()))
                        .toList()));
        Map<Long, JobPostingService.Extracted> extracted = new HashMap<>();
        Map<Long, List<String>> expansions = new HashMap<>();   // 요건 id → 약어 확장어 (검색에만 쓰고 저장하지 않는다)
        if (extraction.lines() != null) {
            extraction.lines().stream().filter(e -> e.id() != null).forEach(e -> {
                extracted.put(e.id(), new JobPostingService.Extracted(e.skillsRaw(), e.minYears()));
                expansions.put(e.id(), e.expansions());
            });
        }
        PostingForAnalysis withSkills = postings.applyExtraction(memberId, posting.postingId(), extracted);
        List<RequirementSnapshot> requirements = withSkills.requirements().stream()
                .map(l -> new RequirementSnapshot(l.requirementId(), l.type(), l.text(), l.order(), l.skills(), l.minYears()))
                .toList();

        // 3. 요건 임베딩 → 4. 요건별 후보 (이 회원의 청크만)
        // 질의는 "원문 (확장어1, 확장어2)" 다. 원문을 남겨야 요건과 경험 중 어느 쪽에 약어가 있어도 걸린다 (기획서 5.4)
        Embeddings queries = ai.embed(                        // 요건 검색은 제목 없이 문구만 보낸다 (기획서 5.5)
                requirements.stream()
                        .map(r -> EmbedItem.of(searchQuery(r.text(), expansions.get(r.requirementId()))))
                        .toList(), EmbedPurpose.QUERY);
        Map<Long, List<Candidate>> candidates = new LinkedHashMap<>();
        for (int i = 0; i < requirements.size(); i++) {
            RequirementSnapshot r = requirements.get(i);
            List<String> keywords = searchKeywords(r.skills(), expansions.get(r.requirementId()));
            candidates.put(r.requirementId(), search.search(memberId, queries.vectors().get(i), keywords, CANDIDATES)
                    .stream()
                    .map(h -> new Candidate(h.chunkId(), EvidenceSourceType.valueOf(h.type().name()), h.sourceId(),
                            h.sourceVersion(), h.content(), h.score()))
                    .toList());
        }

        // 5. 연차 규칙 판정 (경력 재직기간 합집합, 개인·팀 프로젝트 제외)
        List<CareerPeriod> careerPeriods = careerPeriods(memberId);
        YearMonth now = YearMonth.now(ZoneOffset.UTC);

        // 6. 판정·리랭킹 (AI, 묶어서 한 번)
        MatchCall.Result match = ai.match(new MatchCall.Request(requirements.stream()
                .map(r -> new MatchCall.Requirement(r.requirementId(), r.type().toLowerCase(), r.text(), r.skills(),
                        r.minYears(), candidates.get(r.requirementId()).stream().map(FitAnalysisPipeline::toAi).toList()))
                .toList()));
        Map<Long, MatchCall.Judgement> byId = match.results() == null ? Map.of() : match.results().stream()
                .filter(j -> j.id() != null)
                .collect(Collectors.toMap(MatchCall.Judgement::id, j -> j, (a, b) -> a));

        List<Judged> judged = new ArrayList<>();
        for (RequirementSnapshot r : requirements) {
            List<Candidate> own = candidates.get(r.requirementId());
            MatchCall.Judgement j = byId.get(r.requirementId());
            Verdict llm = j == null ? Verdict.UNMET : Verdict.fromAi(j.verdict());
            List<Candidate> evidence = ownedEvidence(j, own);   // 근거 청크 소유권: 이 요건의 후보 안의 것만 (5.4-6)
            if (llm != Verdict.UNMET && evidence.isEmpty()) {
                llm = Verdict.UNMET;   // 근거 없는 충족은 인정하지 않는다
            }
            int months = ExperienceYearsCalculator.months(careerPeriods, r.skills(), now);
            Verdict rule = ExperienceYearsCalculator.judge(r.minYears(), months);
            String reason = j == null || j.reason() == null ? "판정 결과를 받지 못했어요." : j.reason();
            if (rule != null) {
                reason += " (관련 경력 " + ExperienceYearsCalculator.describe(months) + " / 요구 " + r.minYears() + "년)";
            }
            judged.add(new Judged(r, rule, llm, reason, evidence, null));
        }

        // 7. 갭 대응 가이드 — 실패해도 분석은 완료 (ADR-0003). 가이드가 없으면 저장할 때 기본 문구
        return new Output(withGapGuides(judged, candidates, withSkills.tasks()), match.model(), match.promptVersion());
    }

    private List<Judged> withGapGuides(List<Judged> judged, Map<Long, List<Candidate>> candidates, List<String> tasks) {
        List<Judged> gaps = judged.stream().filter(j -> j.finalVerdict() != Verdict.MET).toList();
        if (gaps.isEmpty()) {
            return judged;
        }
        Map<Long, List<Candidate>> adjacent = new HashMap<>();
        for (Judged j : gaps) {
            Set<Long> used = j.evidences().stream().map(Candidate::chunkId).collect(Collectors.toSet());
            adjacent.put(j.requirement().requirementId(), candidates.get(j.requirement().requirementId()).stream()
                    .filter(c -> !used.contains(c.chunkId()))
                    .limit(ADJACENT)
                    .toList());
        }
        Map<Long, GapGuide> guides = new HashMap<>();
        try {
            GapGuideCall.Result result = ai.gapGuide(new GapGuideCall.Request(gaps.stream()
                    .map(j -> new GapGuideCall.Requirement(j.requirement().requirementId(),
                            j.requirement().type().toLowerCase(), j.requirement().text(),
                            j.finalVerdict().name().toLowerCase(),
                            adjacent.get(j.requirement().requirementId()).stream().map(FitAnalysisPipeline::toAi).toList()))
                    .toList(), tasks));
            if (result.guides() != null) {
                for (GapGuideCall.Guide g : result.guides()) {
                    if (g == null || g.id() == null || isBlank(g.advice()) || isBlank(g.avoid())
                            || !adjacent.containsKey(g.id())) {
                        continue;
                    }
                    Set<Long> allowed = adjacent.get(g.id()).stream().map(Candidate::chunkId).collect(Collectors.toSet());
                    List<Long> cited = g.citedChunkIds() == null ? List.of()
                            : g.citedChunkIds().stream().filter(Objects::nonNull).filter(allowed::contains).toList();
                    guides.put(g.id(), new GapGuide(g.advice().strip(), g.avoid().strip(), cited, GapGuide.GapSource.AI));
                }
            }
        } catch (RuntimeException e) {
            log.warn("Gap guide failed; using fallback messages: {}", e.getMessage());
        }
        return judged.stream()
                .map(j -> guides.containsKey(j.requirement().requirementId())
                        ? j.withGapGuide(guides.get(j.requirement().requirementId())) : j)
                .toList();
    }

    private static List<Candidate> ownedEvidence(MatchCall.Judgement judgement, List<Candidate> own) {
        if (judgement == null || judgement.evidenceChunkIds() == null) {
            return List.of();
        }
        Map<Long, Candidate> byId = own.stream().collect(Collectors.toMap(Candidate::chunkId, c -> c, (a, b) -> a));
        return judgement.evidenceChunkIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private List<CareerPeriod> careerPeriods(Long memberId) {
        Map<Long, String> skillNames = userSkills.list(memberId).stream()
                .collect(Collectors.toMap(UserSkillView::id, UserSkillView::name));
        return careers.list(memberId).stream()
                .map(c -> new CareerPeriod(c.startMonth(), c.endMonth(), c.userSkillIds().stream()
                        .map(skillNames::get).filter(Objects::nonNull).collect(Collectors.toSet())))
                .toList();
    }

    /**
     * 요건 검색 질의 — {@code 원문 (확장어1, 확장어2)} (기획서 5.4). 확장어가 없으면 원문 그대로다.
     * 평가에서 MRR 0.86 → 0.94, "MSA 환경" 정답 순위 7위 → 1위 (ADR-0001 "요건 약어 확장 실험").
     */
    static String searchQuery(String text, List<String> expansions) {
        if (expansions == null || expansions.isEmpty()) {
            return text;
        }
        return text + " (" + String.join(", ", expansions) + ")";
    }

    /**
     * 키워드 검색에 쓸 기술명 — 요건의 정규화된 기술명 + 확장어 중 <b>사전에 있는 것</b>.
     * 청크의 {@code skills} 는 표준 기술명이라, 사전에 없는 확장어("관계형 데이터베이스 관리 시스템")는 겹칠 일이
     * 없다. 사전 보강 기록({@code normalizeForPosting})에는 넣지 않는다 — 확장어는 기술명이 아닐 수 있다.
     */
    List<String> searchKeywords(List<String> skills, List<String> expansions) {
        if (expansions == null || expansions.isEmpty()) {
            return skills;
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>(skills);
        expansions.forEach(e -> skillCatalog.find(e).map(SkillSummary::name).ifPresent(keywords::add));
        return List.copyOf(keywords);
    }

    private static MatchCall.Candidate toAi(Candidate c) {
        return new MatchCall.Candidate(c.chunkId(), c.sourceType().name().toLowerCase(), c.content());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
