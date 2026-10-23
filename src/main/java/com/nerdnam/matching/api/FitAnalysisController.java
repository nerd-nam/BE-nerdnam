package com.nerdnam.matching.api;

import com.nerdnam.aijob.api.JobAccepted;
import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.matching.application.FitAnalysisService;
import com.nerdnam.matching.application.FitReportQuery;
import com.nerdnam.matching.application.FitReportView;
import com.nerdnam.matching.application.PostingListQuery;
import com.nerdnam.matching.application.PostingListQuery.PostingSummary;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 적합도 분석 시작·리포트 (S07 → S08), 공고 목록 (S10) */
@RestController
public class FitAnalysisController {

    private final FitAnalysisService analyses;
    private final FitReportQuery reports;
    private final PostingListQuery postingList;

    public FitAnalysisController(FitAnalysisService analyses, FitReportQuery reports, PostingListQuery postingList) {
        this.analyses = analyses;
        this.reports = reports;
        this.postingList = postingList;
    }

    /** 공고 목록 (최근에 고친 것부터) */
    @GetMapping("/api/job-postings")
    public List<PostingSummary> list(@CurrentMemberId Long memberId) {
        return postingList.list(memberId);
    }

    /**
     * "적합도 분석하기" / "다시 시도". 입력 오류면 400 + violations, 같은 공고 분석이 진행 중이면 409.
     * 완료되면 작업의 resultId 가 분석 id.
     */
    @PostMapping("/api/job-postings/{postingId}/analyses")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted start(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return new JobAccepted(analyses.start(memberId, postingId));
    }

    /** 가장 최근 분석의 리포트 (S08) */
    @GetMapping("/api/job-postings/{postingId}/report")
    public FitReportView latest(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return reports.latest(memberId, postingId);
    }

    /** 분석 기록 — 이 공고를 분석한 내역 (최신순). 웹은 여기서 고른 분석을 아래 조회로 본다 */
    @GetMapping("/api/job-postings/{postingId}/analyses")
    public List<FitReportQuery.AnalysisSummary> history(@CurrentMemberId Long memberId, @PathVariable Long postingId) {
        return reports.history(memberId, postingId);
    }

    @GetMapping("/api/fit-analyses/{analysisId}")
    public FitReportView get(@CurrentMemberId Long memberId, @PathVariable Long analysisId) {
        return reports.get(memberId, analysisId);
    }
}
