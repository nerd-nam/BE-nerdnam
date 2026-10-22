package com.nerdnam.guide.api;

import com.nerdnam.aijob.api.JobAccepted;
import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.guide.application.GuideViews;
import com.nerdnam.guide.application.WritingGuideService;
import com.nerdnam.guide.domain.GuideTab;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 작성 방향 (S09). 웹 주소와 같이 공고 id 와 탭 이름(motive·intro·portfolio·career·image)으로 부른다.
 * 공고의 가장 최근 적합도 분석을 기준으로 만든다.
 */
@RestController
@RequestMapping("/api/job-postings/{postingId}/guides")
public class WritingGuideController {

    private final WritingGuideService service;

    public WritingGuideController(WritingGuideService service) {
        this.service = service;
    }

    /** 탭 5개의 상태 (생성 중·완료·실패, 진행 중인 작업 id) */
    @GetMapping
    public GuideViews.Overview overview(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return service.overview(memberId, postingId);
    }

    @GetMapping("/{tab}")
    public GuideViews.Guide get(@CurrentMemberId Long memberId, @PathVariable Long postingId, @PathVariable String tab) {
        return service.get(memberId, postingId, GuideTab.parse(tab));
    }

    /** 탭 만들기 / "이 탭 다시 만들기" — 202 + 작업 id */
    @PostMapping("/{tab}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted generate(@CurrentMemberId Long memberId, @PathVariable Long postingId, @PathVariable String tab,
                                @Valid @RequestBody(required = false) GenerateRequest request) {
        return new JobAccepted(service.generate(memberId, postingId, GuideTab.parse(tab),
                request == null ? null : request.feedback()));
    }

    public record GenerateRequest(@Size(max = 2000) String feedback) {
    }
}
