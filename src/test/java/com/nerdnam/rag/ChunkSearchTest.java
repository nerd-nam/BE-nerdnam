package com.nerdnam.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.rag.application.ChunkSearch;
import com.nerdnam.rag.application.ChunkType;
import com.nerdnam.support.ApiTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 하이브리드 검색의 후보 구성 (D-12, 기획서 v1.39). 청크를 직접 넣어 SQL 만 확인한다.
 *
 * <p>벡터는 손으로 만든다: 1024차원 중 첫 칸만 1이고 나머지는 0인 질의 벡터를 쓰고, 청크 벡터의 첫 칸 값으로
 * 유사도 순서를 정한다(코사인 유사도 = 첫 칸 값). 기술 청크에 높은 값을 줘서 **점수만으로 뽑으면 기술 청크가
 * 후보를 채우는 상황**을 만든 뒤, 종류별 몫이 사례를 지켜 주는지 본다.
 */
class ChunkSearchTest extends ApiTestSupport {

    private static final int DIMENSIONS = Embeddings.DIMENSIONS;   // DB 열과 같은 차원 (ADR-0007: 1024)

    @Autowired
    ChunkSearch search;

    @Test
    void 점수가_높은_기술_청크가_후보를_다_채우지_않고_종류별_몫대로_뽑는다() {
        Long member = newMember();
        // 기술 10개(유사도 0.9대) · 경력 2개 · 프로젝트 3개 · 사례 9개(유사도 0.5대) — 점수만으로는 기술이 상위를 덮는다
        addChunks(member, ChunkType.SKILL, 10, 0.99);
        addChunks(member, ChunkType.CAREER, 2, 0.40);
        addChunks(member, ChunkType.PROJECT_SUMMARY, 3, 0.45);
        addChunks(member, ChunkType.EPISODE, 9, 0.50);

        List<ChunkSearch.Hit> hits = search.search(member, query(), List.of(), 20);

        assertThat(hits).hasSize(20);
        assertThat(count(hits, ChunkType.SKILL)).as("기술은 몫 5개 + 남은 자리").isGreaterThanOrEqualTo(5);
        assertThat(count(hits, ChunkType.EPISODE)).as("점수가 낮아도 사례 몫 8개는 들어온다").isEqualTo(8);
        assertThat(count(hits, ChunkType.CAREER)).as("경력은 2개뿐이라 몫 3개를 못 채운다").isEqualTo(2);
        assertThat(count(hits, ChunkType.PROJECT_SUMMARY)).isEqualTo(3);
        // 경력이 몫보다 적어 남은 자리는 점수 순(기술)으로 채운다
        assertThat(count(hits, ChunkType.SKILL)).isEqualTo(20 - 8 - 2 - 3);
    }

    @Test
    void 돌려주는_순서는_점수가_높은_것부터다() {
        Long member = newMember();
        addChunks(member, ChunkType.SKILL, 3, 0.90);
        addChunks(member, ChunkType.EPISODE, 3, 0.60);

        List<ChunkSearch.Hit> hits = search.search(member, query(), List.of(), 20);

        assertThat(hits).hasSize(6);
        assertThat(hits).extracting(ChunkSearch.Hit::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(hits.getFirst().type()).isEqualTo(ChunkType.SKILL);
    }

    @Test
    void 기술명이_겹치면_점수가_올라_후보_순서가_바뀐다() {
        Long member = newMember();
        addChunks(member, ChunkType.EPISODE, 1, 0.50);                      // 유사도만
        long withSkill = addChunk(member, ChunkType.EPISODE, 0.40, "Redis");  // 유사도는 낮지만 기술명이 겹친다

        List<ChunkSearch.Hit> hits = search.search(member, query(), List.of("Redis"), 20);

        assertThat(hits.getFirst().chunkId()).as("0.7×0.40 + 0.3 > 0.7×0.50").isEqualTo(withSkill);
    }

    @Test
    void 임베딩이_준비되지_않은_청크와_남의_청크는_나오지_않는다() {
        Long member = newMember();
        Long other = newMember();
        long ready = addChunk(member, ChunkType.EPISODE, 0.50, null);
        addPendingChunk(member);   // 임베딩 대기 청크는 벡터가 없다 (ck_chunk_embedding_ready)
        addChunks(other, ChunkType.EPISODE, 3, 0.99);

        assertThat(search.search(member, query(), List.of(), 20)).extracting(ChunkSearch.Hit::chunkId)
                .containsExactly(ready);
    }

    // ------------------------------------------------------------------ 준비

    /** 첫 칸만 1인 질의 벡터 — 청크 벡터의 첫 칸 값이 그대로 코사인 유사도가 된다 */
    private static float[] query() {
        float[] vector = new float[DIMENSIONS];
        vector[0] = 1f;
        return vector;
    }

    private void addChunks(Long memberId, ChunkType type, int count, double similarity) {
        for (int i = 0; i < count; i++) {
            addChunk(memberId, type, similarity - i * 0.001, null);
        }
    }

    /** @param similarity 질의와의 코사인 유사도 (0~1). 첫 칸에 그 값, 둘째 칸에 남은 길이를 넣어 만든다 */
    private long addChunk(Long memberId, ChunkType type, double similarity, String skill) {
        float[] vector = new float[DIMENSIONS];
        vector[0] = (float) similarity;
        vector[1] = (float) Math.sqrt(1 - similarity * similarity);
        return jdbc.sql("""
                        INSERT INTO rag.chunk (member_id, chunk_type, source_id, source_version, title, content,
                                               skills, embedding, embedding_model, embedding_status)
                        VALUES (:m, CAST(:type AS text), :source, 1, '제목', '내용', CAST(:skills AS text[]),
                                CAST(:v AS public.vector), 'stub-embedding:1024', 'READY')
                        RETURNING id
                        """)
                .param("m", memberId)
                .param("type", type.name())
                .param("source", System.nanoTime() % 1_000_000)
                .param("skills", skill == null ? new String[0] : new String[] {skill})
                .param("v", com.nerdnam.rag.infrastructure.Vectors.literal(vector))
                .query(Long.class)
                .single();
    }

    /** 임베딩 대기 청크 (벡터·모델이 없다) */
    private void addPendingChunk(Long memberId) {
        jdbc.sql("""
                        INSERT INTO rag.chunk (member_id, chunk_type, source_id, source_version, title,
                                               content, skills, embedding_status)
                        VALUES (:m, 'EPISODE', :source, 1, '제목', '내용', CAST('{}' AS text[]), 'PENDING')
                        """)
                .param("m", memberId)
                .param("source", System.nanoTime() % 1_000_000)
                .update();
    }

    private static long count(List<ChunkSearch.Hit> hits, ChunkType type) {
        return hits.stream().filter(h -> h.type() == type).count();
    }
}
