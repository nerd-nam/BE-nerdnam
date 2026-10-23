package com.nerdnam.guide.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 작성 방향 탭 하나의 생성 결과 — 애그리거트 루트. (분석, 탭)마다 생성 번호가 1, 2, 3… 늘어나고
 * "다시 만들기"는 새 행을 만든다(이전 결과는 남는다). 생성 중 → 완료 / 실패.
 */
@Entity
@Table(name = "writing_guide")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WritingGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fit_analysis_id", nullable = false, updatable = false)
    private Long fitAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tab", nullable = false, length = 30, updatable = false)
    private GuideTab tab;

    @Column(name = "generation_no", nullable = false, updatable = false)
    private int generationNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GuideStatus status;

    /** 탭 내용 (모양은 AI 계약이 정한다). 인용 청크 id 는 cited_chunk_ids 키로 함께 둔다 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content")
    private Map<String, Object> content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "citation_snapshots")
    private java.util.List<CitationSnapshot> citationSnapshots;

    public void rememberCitations(java.util.List<CitationSnapshot> citations) {
        this.citationSnapshots = java.util.List.copyOf(citations);
    }

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static WritingGuide start(Long fitAnalysisId, GuideTab tab, int generationNo, String feedback) {
        WritingGuide g = new WritingGuide();
        g.fitAnalysisId = Objects.requireNonNull(fitAnalysisId);
        g.tab = Objects.requireNonNull(tab);
        g.generationNo = generationNo;
        g.status = GuideStatus.GENERATING;
        g.feedback = feedback == null || feedback.isBlank() ? null : feedback.strip();
        return g;
    }

    public void complete(GuideContentPolicy.Cleaned cleaned, String aiModel, String promptVersion) {
        if (status != GuideStatus.GENERATING) {
            return;
        }
        java.util.LinkedHashMap<String, Object> stored = new java.util.LinkedHashMap<>(cleaned.content());
        stored.put("cited_chunk_ids", cleaned.citedChunkIds());
        this.content = stored;
        this.aiModel = aiModel;
        this.promptVersion = promptVersion;
        this.status = GuideStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        if (status != GuideStatus.GENERATING) {
            return;
        }
        this.status = GuideStatus.FAILED;
        this.completedAt = Instant.now();
    }
}
