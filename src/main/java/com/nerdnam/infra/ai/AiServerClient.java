package com.nerdnam.infra.ai;

import java.util.List;

/**
 * AI 서버 클라이언트 — 부패 방지 계층의 입구.
 *
 * <p>지금은 상태 확인과 임베딩만 있다. 재구성(/restructure)·판정(/match) 등은 각 묶음에서
 * {@code api-ai.openapi.yaml} 계약(C-04)이 정해지면 추가한다. 요청·응답 모양은 계약 전이라 임시값이다.
 */
public interface AiServerClient {

    /** GET /health — 실패해도 예외를 던지지 않고 ready=false 를 돌려준다 */
    AiServerHealth health();

    /** POST /embed — 실패하면 {@link AiServerUnavailableException} */
    Embeddings embed(List<String> texts, EmbedPurpose purpose);
}
