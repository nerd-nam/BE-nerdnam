package com.nerdnam.infra.ai;

import java.util.Map;

/**
 * AI 서버 상태. ready 가 true 일 때만 "임베딩 대기" 청크를 다시 처리한다 (ADR-0001, 기획서 8장).
 *
 * @param ready  모든 모델이 준비됐는지
 * @param models 모델별 상태 (예: {"llm": "ready", "embedding": "loading"}). 서버에 닿지 못하면 비어 있다
 */
public record AiServerHealth(boolean ready, Map<String, String> models) {

    public static AiServerHealth unreachable() {
        return new AiServerHealth(false, Map.of());
    }
}
