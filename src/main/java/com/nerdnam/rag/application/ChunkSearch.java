package com.nerdnam.rag.application;

import com.nerdnam.infra.ai.AiServerUnavailableException;
import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.rag.infrastructure.Vectors;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 하이브리드 검색 (03 문서 3.8) — 벡터 유사도와 정규화 기술명 겹침을 합산해 점수를 내고,
 * <b>청크 종류별로 몫을 정해 후보를 뽑는다</b> (D-12, 기획서 v1.39). 검색 SQL 은 이 클래스에만 둔다
 * (검색 품질 튜닝 범위를 좁히기 위해). memberId 는 항상 필수다.
 *
 * <p><b>왜 종류별 몫인가:</b> 기술 청크는 짧고 요건과 단어가 겹쳐 점수가 높다. 점수만으로 상위 20개를 뽑으면
 * 평균 15개가 기술 청크여서 사례 정답의 18%가 후보에서 빠졌다. {@code /match} 는 후보 안에서만 근거를 고르므로
 * (기획서 5.5) 후보에서 빠진 사례는 판정에 쓰이지 못한다. 종류별로 나눠 뽑으면 정답 재현율이 0.91 → 0.99,
 * 사례 정답은 0.82 → 0.98 이 됐다 (ADR-0001 "검색 후보 구성 비교", 사람이 쓴 4명 데이터).
 *
 * <p>점수 기준선으로 후보를 거르지 않는다 — 항상 상위 후보를 /match 로 넘겨 판정한다 (ADR-0001 평가 결과).
 * 가중치(0.7 / 0.3)와 몫(5/3/4/8)은 임시값이다. 실제 사용자의 기술·사례 수를 보고 다시 정한다.
 */
@Component
public class ChunkSearch {

    static final double VECTOR_WEIGHT = 0.7;
    static final double KEYWORD_WEIGHT = 0.3;

    /** 후보 20개를 종류별로 나눈 몫 (D-12). 모자라면 남은 자리를 점수 순으로 채운다 */
    static final Map<ChunkType, Integer> QUOTAS = new EnumMap<>(Map.of(
            ChunkType.SKILL, 5,
            ChunkType.CAREER, 3,
            ChunkType.PROJECT_SUMMARY, 4,
            ChunkType.EPISODE, 8));

    private final JdbcClient jdbc;

    public ChunkSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Hit(long chunkId, ChunkType type, long sourceId, int sourceVersion, Long projectId, String content,
                      double score) {
    }

    /**
     * 요건 하나의 후보 청크. 돌려주는 순서는 점수가 높은 것부터다.
     *
     * @param skills 키워드 검색에 쓸 표준 기술명 (요건에서 뽑아 정규화한 것 + 사전에 있는 약어 확장어)
     * @param limit  후보 수 (분석은 20개를 쓴다)
     */
    public List<Hit> search(long memberId, float[] queryVector, Collection<String> skills, int limit) {
        Objects.requireNonNull(queryVector, "queryVector");
        // 차원이 다르면 pgvector 가 SQL 안에서 "different vector dimensions" 로 터지고, 작업은 internal 로만
        // 실패해 원인이 안 보인다 (이슈 #31). 모델을 바꾼 뒤 재임베딩이 끝나지 않았을 때 생긴다
        if (queryVector.length != Embeddings.DIMENSIONS) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR,
                    "search: 질의 벡터 %d차원 ≠ rag.chunk.embedding vector(%d) — 임베딩 모델이 바뀠는지 확인하세요"
                            .formatted(queryVector.length, Embeddings.DIMENSIONS), null);
        }
        // 한 회원의 청크는 수십 개 수준이라(ADR-0001 평가 데이터 36~39개) member_id 로 좁힌 뒤 전부 점수를 낸다.
        // 종류별 순위를 내려면 종류마다 충분한 후보가 있어야 해서, 점수 상위 20개로 먼저 줄이지 않는다 —
        // 그렇게 줄이면 기술 청크가 자리를 채워 사례가 애초에 순위에 들지 못한다 (D-12 가 고치려는 문제).
        return jdbc.sql("""
                        WITH scored AS (
                            SELECT id, chunk_type, source_id, source_version, project_id, content,
                                   (1 - (embedding <=> CAST(:q AS public.vector))) * :vw
                                   + CASE WHEN skills && CAST(:skills AS text[]) THEN :kw ELSE 0 END AS score
                            FROM rag.chunk
                            WHERE member_id = :m AND embedding_status = 'READY'
                        ), ranked AS (
                            SELECT scored.*,
                                   ROW_NUMBER() OVER (PARTITION BY chunk_type ORDER BY score DESC, id) AS type_rank
                            FROM scored
                        ), picked AS (
                            SELECT ranked.*,
                                   CASE WHEN type_rank <= CASE chunk_type
                                                              WHEN 'SKILL' THEN :skill_quota
                                                              WHEN 'CAREER' THEN :career_quota
                                                              WHEN 'PROJECT_SUMMARY' THEN :project_quota
                                                              WHEN 'EPISODE' THEN :episode_quota
                                                              ELSE 0 END
                                        THEN 0 ELSE 1 END AS tier
                            FROM ranked
                        )
                        SELECT id, chunk_type, source_id, source_version, project_id, content, score
                        FROM (SELECT * FROM picked ORDER BY tier, score DESC, id LIMIT :limit) chosen
                        ORDER BY score DESC, id
                        """)
                .param("q", Vectors.literal(queryVector))
                .param("m", memberId)
                .param("skills", skills.toArray(String[]::new))
                .param("vw", VECTOR_WEIGHT)
                .param("kw", KEYWORD_WEIGHT)
                .param("skill_quota", QUOTAS.get(ChunkType.SKILL))
                .param("career_quota", QUOTAS.get(ChunkType.CAREER))
                .param("project_quota", QUOTAS.get(ChunkType.PROJECT_SUMMARY))
                .param("episode_quota", QUOTAS.get(ChunkType.EPISODE))
                .param("limit", limit)
                .query((rs, n) -> new Hit(rs.getLong(1), ChunkType.valueOf(rs.getString(2)), rs.getLong(3), rs.getInt(4),
                        (Long) rs.getObject(5), rs.getString(6), rs.getDouble(7)))
                .list();
    }
}
