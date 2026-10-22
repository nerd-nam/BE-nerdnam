package com.nerdnam.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 확인 질문에 사용자가 답한 내용. 원문 뒤에 순서대로 덧붙여 AI 입력 원문을 만든다 (03 문서 3.4).
 * 원문(raw_text)은 사용자가 쓴 그대로 두기 위해 따로 저장한다.
 */
@Entity
@Table(name = "project_supplement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectSupplement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    /** 어떤 사례의 질문이었는지 (사례가 대체돼도 기록은 남김, FK 없음) */
    @Column(name = "episode_id", updatable = false)
    private Long episodeId;

    @Column(name = "question", nullable = false, updatable = false)
    private String question;

    @Column(name = "answer", nullable = false, updatable = false)
    private String answer;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    static ProjectSupplement of(Project project, Long episodeId, String question, String answer) {
        ProjectSupplement s = new ProjectSupplement();
        s.project = project;
        s.episodeId = episodeId;
        s.question = question;
        s.answer = answer.strip();
        return s;
    }

    /** 원문 뒤에 붙는 모양. 바꾸면 기존 source_spans 의 보충 답변 구간이 어긋나므로 바꾸지 않는다 */
    String asSourceText() {
        return "질문: " + question + "\n답변: " + answer;
    }
}
