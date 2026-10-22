package com.nerdnam.matching.domain;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판정 근거 하나. 청크는 재등록 때 지워질 수 있으므로 chunk_id 에 FK 를 두지 않고
 * 원본 출처(종류·id·버전)와 근거 문장(snippet)을 함께 저장한다 (03 문서 3.6).
 */
@Entity
@Table(name = "match_evidence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requirement_match_id", nullable = false, updatable = false)
    private RequirementMatch match;

    @Column(name = "rank_no", nullable = false)
    private int rankNo;

    @Column(name = "chunk_id", nullable = false)
    private long chunkId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private EvidenceSourceType sourceType;

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(name = "source_version", nullable = false)
    private int sourceVersion;

    @Column(name = "snippet", nullable = false)
    private String snippet;

    static MatchEvidence of(RequirementMatch match, int rankNo, Candidate c) {
        MatchEvidence e = new MatchEvidence();
        e.match = match;
        e.rankNo = rankNo;
        e.chunkId = c.chunkId();
        e.sourceType = c.sourceType();
        e.sourceId = c.sourceId();
        e.sourceVersion = c.sourceVersion();
        e.snippet = c.content();
        return e;
    }

    /** 화면에 보여줄 이름 (근거 카드 제목). 청크 텍스트의 첫 줄들에서 꺼낸다 (rag 의 ChunkTextBuilder 모양) */
    public String label() {
        return EvidenceLabel.of(sourceType, snippet);
    }
}
