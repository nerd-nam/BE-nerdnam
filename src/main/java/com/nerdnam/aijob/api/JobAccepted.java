package com.nerdnam.aijob.api;

/** AI 작업을 접수했을 때의 응답 (202). 웹은 jobId 로 GET /api/ai-jobs/{jobId}를 조회한다 */
public record JobAccepted(Long jobId) {
}
