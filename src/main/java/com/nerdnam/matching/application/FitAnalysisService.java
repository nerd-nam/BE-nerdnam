package com.nerdnam.matching.application;

import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.aijob.application.JobRequest;
import com.nerdnam.aijob.application.JobWork;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.PostingForAnalysis;
import com.nerdnam.matching.domain.FitAnalysis;
import com.nerdnam.matching.domain.FitAnalysisRepository;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * 적합도 분석 시작 (S07 "적합도 분석하기", S08 "다시 시도"). AI 작업 하나로 돌고, 완료되면 작업의 resultId 가 분석 id.
 * 같은 공고의 분석은 동시에 1건만. 공고를 고쳐 다시 분석하면 새 분석이 쌓인다 (이전 리포트는 그대로).
 */
@Service
public class FitAnalysisService {

    private final AiJobs aiJobs;
    private final JobPostingService postings;
    private final FitAnalysisRepository analyses;
    private final FitAnalysisPipeline pipeline;

    public FitAnalysisService(AiJobs aiJobs, JobPostingService postings, FitAnalysisRepository analyses,
                              FitAnalysisPipeline pipeline) {
        this.aiJobs = aiJobs;
        this.postings = postings;
        this.analyses = analyses;
        this.pipeline = pipeline;
    }

    /** 입력 검사에 걸리면 400 + violations (작업은 만들지 않는다). 통과하면 작업 id */
    public Long start(Long memberId, Long postingId) {
        AtomicReference<Long> analysisId = new AtomicReference<>();
        JobWork<Started, FitAnalysisPipeline.Output> work = JobWork.<Started, FitAnalysisPipeline.Output>of(
                () -> {
                    PostingForAnalysis posting = postings.prepareAnalysis(memberId, postingId);
                    FitAnalysis analysis = analyses.saveAndFlush(FitAnalysis.start(memberId, postingId));
                    analysisId.set(analysis.getId());
                    return new Started(analysis.getId(), posting);
                },
                started -> pipeline.run(memberId, started.posting()),
                output -> {
                    FitAnalysis analysis = analyses.findById(analysisId.get()).orElseThrow();
                    analysis.complete(output.judged(), output.aiModel(), output.promptVersion());
                    return analysis.getId();
                })
                .onFailure(code -> {
                    if (analysisId.get() != null) {
                        analyses.findById(analysisId.get()).ifPresent(a -> a.fail(code.name()));
                    }
                });
        return aiJobs.start(JobRequest.of(memberId, JobType.FIT_ANALYSIS, null, TargetType.JOB_POSTING, postingId), work);
    }

    private record Started(Long analysisId, PostingForAnalysis posting) {
    }
}
