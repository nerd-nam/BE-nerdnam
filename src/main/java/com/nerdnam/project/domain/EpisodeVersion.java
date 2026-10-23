package com.nerdnam.project.domain;

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
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사례 버전 — 한 번 저장하면 바꾸지 않는다 (append-only, P5).
 * AI 가 만든 구조 데이터(사용 기술·성과·원문 구간·확인 질문)는 jsonb 로 통째로 저장한다 (03 문서 3.2).
 */
@Entity
@Table(name = "episode_version")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EpisodeVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "episode_id", nullable = false, updatable = false)
    private Episode episode;

    @Column(name = "version_no", nullable = false, updatable = false)
    private int versionNo;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "role", length = 200)
    private String role;

    @Column(name = "problem", nullable = false)
    private String problem;

    @Column(name = "solution", nullable = false)
    private String solution;

    @Column(name = "result", nullable = false)
    private String result;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skills", nullable = false)
    private List<String> skills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", nullable = false)
    private List<Metric> metrics;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_spans", nullable = false)
    private List<SourceSpan> sourceSpans;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clarifications", nullable = false)
    private List<Clarification> clarifications;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "restored_from")
    private Integer restoredFrom;

    @Column(name = "feedback")
    private String feedback;

    @Column(name = "ai_model", length = 100)
    private String aiModel;

    @Column(name = "prompt_version", length = 50)
    private String promptVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static EpisodeVersion of(Episode episode, int versionNo, EpisodeContent content, ChangeType changeType,
                             Integer restoredFrom, String feedback, AiTrace trace) {
        EpisodeVersion v = new EpisodeVersion();
        v.episode = episode;
        v.versionNo = versionNo;
        v.title = content.title();
        v.role = content.role();
        v.problem = content.problem();
        v.solution = content.solution();
        v.result = content.result();
        v.skills = content.skills();
        v.metrics = content.metrics();
        v.sourceSpans = content.sourceSpans();
        v.clarifications = content.clarifications();
        v.changeType = changeType;
        v.restoredFrom = restoredFrom;
        v.feedback = feedback == null || feedback.isBlank() ? null : feedback.strip();
        AiTrace t = trace == null ? AiTrace.none() : trace;
        v.aiModel = t.aiModel();
        v.promptVersion = t.promptVersion();
        return v;
    }

    public EpisodeContent content() {
        return new EpisodeContent(title, role, problem, solution, result, skills, metrics, sourceSpans, clarifications);
    }
}
