package com.nerdnam.infra.ai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 개발·테스트용 가짜 AI 서버 (local 프로필 기본, nerdnam.ai.stub=true).
 * 같은 텍스트·용도에는 항상 같은 벡터를 돌려주므로 테스트 결과가 매번 같다.
 */
public class StubAiServerClient implements AiServerClient {

    public static final int DIMENSIONS = 768;   // embeddinggemma-2 와 같은 차원 (rag.chunk.embedding)
    public static final String MODEL = "stub-embedding:768";

    @Override
    public AiServerHealth health() {
        return new AiServerHealth(true, Map.of("llm", "ready", "embedding", "ready"));
    }

    @Override
    public Embeddings embed(List<String> texts, EmbedPurpose purpose) {
        List<float[]> vectors = texts.stream().map(text -> vectorOf(text, purpose)).toList();
        return new Embeddings(vectors, MODEL);
    }

    /** 텍스트 내용으로 시드를 정해 정규분포 값을 뽑고, 길이 1로 정규화한다 (코사인 거리 계산에 맞게) */
    static float[] vectorOf(String text, EmbedPurpose purpose) {
        long seed = 1125899906842597L;
        for (byte b : (purpose.name() + ":" + text).getBytes(StandardCharsets.UTF_8)) {
            seed = 31 * seed + b;
        }
        Random random = new Random(seed);
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) random.nextGaussian();
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
