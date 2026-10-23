package com.nerdnam.infra.ai;

import java.util.List;

/**
 * 임베딩 결과. vectors 는 요청한 텍스트 순서와 같고 길이 1로 정규화돼 있다 (코사인 거리).
 * embeddingModel 은 청크마다 저장해 모델 교체 시 재임베딩 대상을 찾는 데 쓴다 (예: google/embeddinggemma-2@rev:768).
 */
public record Embeddings(List<float[]> vectors, String embeddingModel) {

    /** AI 서버가 가짜 응답(stub) 모드일 때 돌려주는 모델 이름 (기획서 5.5) */
    public static final String STUB_MODEL = "stub";

    /** 가짜 응답(stub)이거나 모델 이름이 없으면 벡터를 저장하지 않는다 — 청크를 "임베딩 대기"로 둔다 */
    public boolean stub() {
        return embeddingModel == null || embeddingModel.isBlank()
                || STUB_MODEL.equalsIgnoreCase(embeddingModel.trim());
    }
}
