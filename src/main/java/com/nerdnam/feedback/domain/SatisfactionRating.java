package com.nerdnam.feedback.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 만족도 평가 하나 — 회원·대상마다 하나, 다시 평가하면 덮어쓴다 (기획서 4.6) */
@Entity
@Table(name = "satisfaction_rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SatisfactionRating extends BaseTimeEntity {

    public static final int COMMENT_MAX = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private RatingTarget targetType;

    @Column(name = "target_id", nullable = false, updatable = false)
    private Long targetId;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "comment", length = COMMENT_MAX)
    private String comment;

    @Column(name = "split_round")
    private Integer splitRound;

    @Column(name = "fit_analysis_id")
    private Long fitAnalysisId;

    public static SatisfactionRating of(Long memberId, RatingTarget type, Long targetId) {
        SatisfactionRating r = new SatisfactionRating();
        r.memberId = Objects.requireNonNull(memberId);
        r.targetType = Objects.requireNonNull(type);
        r.targetId = Objects.requireNonNull(targetId);
        return r;
    }

    /** 점수 1~5, 한 줄 의견은 선택. 평가할 때의 회차를 함께 남긴다 */
    public void rate(int score, String comment, Integer splitRound, Long fitAnalysisId) {
        if (score < 1 || score > 5) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "별점은 1~5 사이로 골라 주세요.");
        }
        String c = comment == null || comment.isBlank() ? null : comment.strip();
        if (c != null && c.length() > COMMENT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "의견은 " + COMMENT_MAX + "자 이하로 입력해 주세요.");
        }
        this.score = score;
        this.comment = c;
        this.splitRound = splitRound;
        this.fitAnalysisId = fitAnalysisId;
    }
}
