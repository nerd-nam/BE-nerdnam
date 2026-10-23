package com.nerdnam.feedback.api;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.feedback.application.RatingService;
import com.nerdnam.feedback.application.RatingService.RatingView;
import com.nerdnam.feedback.domain.RatingTarget;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 만족도 평가: S05 사례 생성 평가(프로젝트), S09 방향 제시 평가(공고). 평가가 없으면 GET 은 204 */
@RestController
public class RatingController {

    private final RatingService service;

    public RatingController(RatingService service) {
        this.service = service;
    }

    @GetMapping("/api/projects/{projectId}/rating")
    public ResponseEntity<RatingView> projectRating(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return of(service.get(memberId, RatingTarget.EPISODE_GENERATION, projectId));
    }

    @PutMapping("/api/projects/{projectId}/rating")
    public RatingView rateProject(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                                  @Valid @RequestBody RatingRequest request) {
        return service.rate(memberId, RatingTarget.EPISODE_GENERATION, projectId, request.score(), request.comment());
    }

    @GetMapping("/api/job-postings/{postingId}/guides/rating")
    public ResponseEntity<RatingView> guideRating(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return of(service.get(memberId, RatingTarget.GUIDE, postingId));
    }

    @PutMapping("/api/job-postings/{postingId}/guides/rating")
    public RatingView rateGuide(@CurrentMemberId Long memberId, @PathVariable Long postingId,
                                @Valid @RequestBody RatingRequest request) {
        return service.rate(memberId, RatingTarget.GUIDE, postingId, request.score(), request.comment());
    }

    public record RatingRequest(
            @NotNull(message = "별점을 골라 주세요.") @Min(1) @Max(5) Integer score,
            @Size(max = 300, message = "의견은 300자 이하로 입력해 주세요.") String comment) {
    }

    private static ResponseEntity<RatingView> of(java.util.Optional<RatingView> rating) {
        return rating.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
