package com.nerdnam.infra.ai;

import java.util.List;

/**
 * 임베딩 결과. vectors 는 요청한 텍스트 순서와 같고 길이 1로 정규화돼 있다 (코사인 거리).
 * embeddingModel 은 청크마다 저장해 모델 교체 시 재임베딩 대상을 찾는 데 쓴다
 * (예: amazon.titan-embed-text-v2:0:1024).
 */
public record Embeddings(List<float[]> vectors, String embeddingModel) {

    /** AI 서버가 가짜 응답(stub) 모드일 때 돌려주는 모델 이름 (기획서 5.5) */
    public static final String STUB_MODEL = "stub";

    /**
     * 저장할 벡터의 차원. <b>{@code rag.chunk.embedding} 의 {@code vector(1024)} 와 같아야 한다</b>
     * (ADR-0007 확정: Amazon Titan Text Embeddings V2, 1024). 차원이 다른 벡터를 넣거나 질의로 쓰면 pgvector 가
     * "different vector dimensions" 로 거절하는데, 그 오류는 SQL 에서 터져 원인이 드러나지 않는다
     * (이슈 #31 에서 작업이 {@code internal} 로만 실패했다). 그래서 여기서 먼저 막는다.
     * 차원을 바꿀 때는 마이그레이션(열 타입)과 이 값을 함께 바꾸고 전체를 다시 임베딩한다.
     */
    public static final int DIMENSIONS = 1024;

    /** 가짜 응답(stub)이거나 모델 이름이 없으면 벡터를 저장하지 않는다 — 청크를 "임베딩 대기"로 둔다 */
    public boolean stub() {
        return embeddingModel == null || embeddingModel.isBlank()
                || STUB_MODEL.equalsIgnoreCase(embeddingModel.trim());
    }

    /** 차원이 DB 열과 다른 벡터가 있나 (있으면 저장·검색에 쓸 수 없다) */
    public boolean wrongDimensions() {
        return vectors.stream().anyMatch(v -> v == null || v.length != DIMENSIONS);
    }

    /** 사람이 읽을 차원 설명 — 로그·오류 메시지에 쓴다 */
    public String dimensionsText() {
        return vectors.stream().findFirst().map(v -> v == null ? "없음" : String.valueOf(v.length)).orElse("빈 응답");
    }
}
