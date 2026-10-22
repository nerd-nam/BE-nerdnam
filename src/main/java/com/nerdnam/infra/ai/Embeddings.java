package com.nerdnam.infra.ai;

import java.util.List;

/**
 * 임베딩 결과. vectors 는 요청한 텍스트 순서와 같다.
 * embeddingModel 은 청크마다 저장해 모델 교체 시 재임베딩 대상을 찾는 데 쓴다 (예: google/embeddinggemma-2@rev:768).
 */
public record Embeddings(List<float[]> vectors, String embeddingModel) {
}
