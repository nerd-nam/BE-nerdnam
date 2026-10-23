package com.nerdnam.infra.ai;

import java.util.Map;

/**
 * AI 서버 상태. embedding 모델이 ready 일 때만 "임베딩 대기" 청크를 다시 처리한다 (ADR-0001, 기획서 8장).
 *
 * @param ready  모든 모델이 준비됐는지
 * @param models 모델별 상태 (예: {"llm": "ready", "embedding": "loading"}). 서버에 닿지 못하면 비어 있다
 */
public record AiServerHealth(boolean ready, Map<String, String> models) {

    /** 생성 모델이 꺼져 있어도 임베딩만 준비됐다면 대기 청크를 처리한다 (기획서 v1.29). */
    public boolean embeddingReady() {
        return models != null && "ready".equals(models.get("embedding"));
    }

    public static AiServerHealth unreachable() {
        return new AiServerHealth(false, Map.of());
    }
}
