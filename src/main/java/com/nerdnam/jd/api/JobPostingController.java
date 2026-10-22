package com.nerdnam.jd.api;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.JobPostingView;
import com.nerdnam.jd.domain.PostingContent;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공고 입력 (S07). 임시 저장은 검사하지 않는다. 검사는 분석 시작(POST /api/job-postings/{id}/analyses) 때.
 * 공고 목록(S10)은 분석 상태가 필요해 matching 모듈의 PostingListController 가 맡는다.
 */
@RestController
@RequestMapping("/api/job-postings")
public class JobPostingController {

    private final JobPostingService service;

    public JobPostingController(JobPostingService service) {
        this.service = service;
    }

    /** 새 공고의 첫 임시 저장 — 응답의 id 로 주소를 /apply/{id}/jd 로 바꾼다 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobPostingView create(@CurrentMemberId Long memberId, @Valid @RequestBody(required = false) PostingRequest request) {
        return service.create(memberId, request == null ? PostingContent.empty() : request.toContent());
    }

    @GetMapping("/{postingId}")
    public JobPostingView get(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return service.get(memberId, postingId);
    }

    /** 자동 임시 저장. version 은 조회 때 받은 값 (다르면 409) */
    @PutMapping("/{postingId}")
    public JobPostingView update(@CurrentMemberId Long memberId, @PathVariable Long postingId,
                                 @Valid @RequestBody PostingRequest request) {
        return service.update(memberId, postingId, request.toContent(), request.version());
    }

    @DeleteMapping("/{postingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        service.delete(memberId, postingId);
    }

    /** 줄 목록은 한 줄에 요건 하나. 빈 줄은 저장할 때 버린다 */
    public record PostingRequest(String companyName, String positionName, String postingUrl, String companyIntro,
                                 List<String> required, List<String> preferred, List<String> tasks, Long version) {

        PostingContent toContent() {
            return new PostingContent(companyName, positionName, postingUrl, companyIntro, required, preferred, tasks);
        }
    }
}
