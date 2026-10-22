package com.nerdnam.infra.ai;

import java.util.List;

/**
 * AI 서버 클라이언트 — 부패 방지 계층의 입구. 다른 모듈은 이 인터페이스와 이 패키지의 요청·응답 타입만 쓴다.
 *
 * <p>요청·응답 모양은 계약(C-04 {@code api-ai.openapi.yaml}) 전의 초안이다 → docs/06-AI-계약-초안.md.
 * 모든 호출은 실패하면 {@link AiServerUnavailableException}(GPU 꺼짐 / 시간 초과 / AI 오류)을 던진다.
 */
public interface AiServerClient {

    /** GET /health — 실패해도 예외를 던지지 않고 ready=false 를 돌려준다 */
    AiServerHealth health();

    /** POST /embed */
    Embeddings embed(List<String> texts, EmbedPurpose purpose);

    /** POST /restructure (P1) — 나누기·다시 나누기·사례 고치기 */
    RestructureResult restructure(RestructureRequest request);

    /** POST /requirements/extract (P2) */
    RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request);

    /** POST /match (P3) */
    MatchCall.Result match(MatchCall.Request request);

    /** POST /gap-guide (P6) */
    GapGuideCall.Result gapGuide(GapGuideCall.Request request);

    /** POST /guide (P4·P5, SSE 를 끝까지 받아 모은 결과) */
    GuideCall.Result guide(GuideCall.Request request);
}
