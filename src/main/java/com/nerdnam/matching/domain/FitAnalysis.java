package com.nerdnam.matching.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 적합도 분석 1회 — 애그리거트 루트 (02 문서 4장). 공고를 고쳐 다시 분석하면 새 분석이 쌓이고, 화면은 가장 최근 것을 본다.
 * 진행 중 → 완료(요건별 판정 저장) 또는 실패.
 */
@Entity
@Table(name = "fit_analysis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FitAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "job_posting_id", nullable = false, updatable = false)
    private Long jobPostingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @OneToMany(mappedBy = "analysis", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder")
    private List<RequirementMatch> matches = new ArrayList<>();

    public static FitAnalysis start(Long memberId, Long jobPostingId) {
        FitAnalysis a = new FitAnalysis();
        a.memberId = Objects.requireNonNull(memberId);
        a.jobPostingId = Objects.requireNonNull(jobPostingId);
        a.status = AnalysisStatus.IN_PROGRESS;
        a.requestedAt = Instant.now();
        return a;
    }

    public void complete(List<RequirementMatch.Judged> judged, String aiModel, String promptVersion) {
        if (status != AnalysisStatus.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "이미 끝난 분석이에요.");
        }
        judged.forEach(j -> matches.add(RequirementMatch.of(this, j)));
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.status = AnalysisStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String code) {
        if (status != AnalysisStatus.IN_PROGRESS) {
            return;
        }
        this.status = AnalysisStatus.FAILED;
        this.failureCode = code;
        this.completedAt = Instant.now();
    }

    /** 충족률·한 줄 진단은 저장하지 않고 조회할 때 계산한다 (공식을 바꿔도 데이터 이전이 필요 없다) */
    public FitReport report() {
        return FitReport.of(matches);
    }

    public List<RequirementMatch> getMatches() {
        return Collections.unmodifiableList(matches);
    }
}
