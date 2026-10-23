package com.nerdnam.feedback.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface SatisfactionRatingRepository extends Repository<SatisfactionRating, Long> {

    SatisfactionRating saveAndFlush(SatisfactionRating rating);

    Optional<SatisfactionRating> findByMemberIdAndTargetTypeAndTargetId(Long memberId, RatingTarget type, Long targetId);
}
