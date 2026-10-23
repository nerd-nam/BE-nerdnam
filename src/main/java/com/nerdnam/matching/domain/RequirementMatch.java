package com.nerdnam.matching.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 요건 하나의 판정 결과 + 근거 + 갭 대응 가이드 */
@Entity
@Table(name = "requirement_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RequirementMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fit_analysis_id", nullable = false, updatable = false)
    private FitAnalysis analysis;

    /** FK 없음: 요건이 고쳐지거나 지워져도 결과는 남는다 */
    @Column(name = "requirement_id")
    private Long requirementId;

    @Column(name = "requirement_type", nullable = false, length = 20)
    private String requirementType;

    @Column(name = "requirement_text", nullable = false, length = 1000)
    private String requirementText;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", nullable = false)
    private List<String> skills;

    @Column(name = "min_years")
    private Integer minYears;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_verdict", length = 20)
    private Verdict ruleVerdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_verdict", length = 20)
    private Verdict llmVerdict;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_verdict", nullable = false, length = 20)
    private Verdict finalVerdict;

    @Column(name = "reason")
    private String reason;

    @Column(name = "gap_advice")
    private String gapAdvice;

    @Column(name = "gap_avoid")
    private String gapAvoid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gap_cited_chunk_ids")
    private List<Long> gapCitedChunkIds;

    @Enumerated(EnumType.STRING)
    @Column(name = "gap_source", length = 20)
    private GapGuide.GapSource gapSource;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rankNo")
    private List<MatchEvidence> evidences = new ArrayList<>();

    static RequirementMatch of(FitAnalysis analysis, Judged judged) {
        RequirementSnapshot req = judged.requirement();
        RequirementMatch m = new RequirementMatch();
        m.analysis = analysis;
        m.requirementId = req.requirementId();
        m.requirementType = req.type();
        m.requirementText = req.text();
        m.displayOrder = req.displayOrder();
        m.skills = req.skills();
        m.minYears = req.minYears();
        m.ruleVerdict = judged.ruleVerdict();
        m.llmVerdict = judged.llmVerdict();
        m.finalVerdict = judged.finalVerdict();
        m.reason = judged.reason();
        int rank = 1;
        for (Candidate c : judged.evidences()) {
            m.evidences.add(MatchEvidence.of(m, rank++, c));
        }
        if (judged.finalVerdict() != Verdict.MET) {
            GapGuide gap = judged.gapGuide() == null ? GapGuide.fallbackFor(judged.finalVerdict()) : judged.gapGuide();
            m.gapAdvice = gap.advice();
            m.gapAvoid = gap.avoid();
            m.gapCitedChunkIds = gap.citedChunkIds();
            m.gapSource = gap.source();
        }
        return m;
    }

    public boolean isRequired() {
        return "REQUIRED".equals(requirementType);
    }

    public GapGuide gapGuide() {
        return gapSource == null ? null : new GapGuide(gapAdvice, gapAvoid, gapCitedChunkIds, gapSource);
    }

    public List<MatchEvidence> getEvidences() {
        return Collections.unmodifiableList(evidences);
    }

    /**
     * 판정이 끝난 요건 하나 (저장 전). 최종 판정은 규칙·LLM 중 낮은 쪽 (D-5).
     * 근거는 최대 5개 (기획서 5.4-4).
     */
    public record Judged(RequirementSnapshot requirement, Verdict ruleVerdict, Verdict llmVerdict, String reason,
                         List<Candidate> evidences, GapGuide gapGuide) {

        public Judged {
            evidences = evidences == null ? List.of() : List.copyOf(evidences.subList(0, Math.min(5, evidences.size())));
        }

        public Verdict finalVerdict() {
            return VerdictPolicy.combine(ruleVerdict, llmVerdict);
        }

        public Judged withGapGuide(GapGuide guide) {
            return new Judged(requirement, ruleVerdict, llmVerdict, reason, evidences, guide);
        }
    }
}
