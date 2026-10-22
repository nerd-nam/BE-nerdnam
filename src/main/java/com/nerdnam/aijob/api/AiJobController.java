package com.nerdnam.aijob.api;

import com.nerdnam.aijob.application.AiJobView;
import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.common.auth.CurrentMemberId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 작업 상태 (C-02 공통 3: 웹이 몇 초마다 조회) */
@RestController
@RequestMapping("/api/ai-jobs")
public class AiJobController {

    private final AiJobs aiJobs;

    public AiJobController(AiJobs aiJobs) {
        this.aiJobs = aiJobs;
    }

    @GetMapping("/{jobId}")
    public AiJobView get(@CurrentMemberId Long memberId, @PathVariable Long jobId) {
        return aiJobs.get(memberId, jobId);
    }
}
