package com.nerdnam.feedback.application;

import com.nerdnam.feedback.domain.RatingTarget;
import com.nerdnam.feedback.domain.SatisfactionRating;
import com.nerdnam.feedback.domain.SatisfactionRatingRepository;
import com.nerdnam.matching.application.FitReportQuery;
import com.nerdnam.project.application.ProjectDraftService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 만족도 평가 저장·조회. 대상(프로젝트·공고)이 내 것인지 먼저 확인한다 */
@Service
@Transactional
public class RatingService {

    private final SatisfactionRatingRepository ratings;
    private final ProjectDraftService projects;
    private final FitReportQuery reports;

    public RatingService(SatisfactionRatingRepository ratings, ProjectDraftService projects, FitReportQuery reports) {
        this.ratings = ratings;
        this.projects = projects;
        this.reports = reports;
    }

    public record RatingView(RatingTarget targetType, Long targetId, int score, String comment, Instant updatedAt) {

        static RatingView from(SatisfactionRating r) {
            return new RatingView(r.getTargetType(), r.getTargetId(), r.getScore(), r.getComment(), r.getUpdatedAt());
        }
    }

    @Transactional(readOnly = true)
    public Optional<RatingView> get(Long memberId, RatingTarget type, Long targetId) {
        checkOwner(memberId, type, targetId);
        return ratings.findByMemberIdAndTargetTypeAndTargetId(memberId, type, targetId).map(RatingView::from);
    }

    /** 다시 보내면 덮어쓴다 */
    public RatingView rate(Long memberId, RatingTarget type, Long targetId, int score, String comment) {
        Integer splitRound = null;
        Long analysisId = null;
        if (type == RatingTarget.EPISODE_GENERATION) {
            splitRound = projects.get(memberId, targetId).splitRound();
        } else {
            analysisId = reports.latest(memberId, targetId).analysisId();
        }
        SatisfactionRating rating = ratings.findByMemberIdAndTargetTypeAndTargetId(memberId, type, targetId)
                .orElseGet(() -> SatisfactionRating.of(memberId, type, targetId));
        rating.rate(score, comment, splitRound, analysisId);
        return RatingView.from(ratings.saveAndFlush(rating));
    }

    private void checkOwner(Long memberId, RatingTarget type, Long targetId) {
        if (type == RatingTarget.EPISODE_GENERATION) {
            projects.get(memberId, targetId);
        } else {
            reports.latest(memberId, targetId);
        }
    }
}
