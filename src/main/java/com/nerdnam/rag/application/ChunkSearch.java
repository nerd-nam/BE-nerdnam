package com.nerdnam.rag.application;

import com.nerdnam.rag.infrastructure.Vectors;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 하이브리드 검색 (03 문서 3.8) — 벡터 유사도 상위 20 + 정규화 기술명이 겹치는 청크, 점수 합산.
 * 검색 SQL 은 이 클래스에만 둔다 (검색 품질 튜닝 범위를 좁히기 위해). memberId 는 항상 필수다.
 *
 * <p>점수 기준선으로 후보를 거르지 않는다 — 항상 상위 후보를 /match 로 넘겨 판정한다 (ADR-0001 평가 결과).
 * 가중치(0.7 / 0.3)는 임시값이다.
 */
@Component
public class ChunkSearch {

    static final double VECTOR_WEIGHT = 0.7;
    static final double KEYWORD_WEIGHT = 0.3;

    private final JdbcClient jdbc;

    public ChunkSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Hit(long chunkId, ChunkType type, long sourceId, int sourceVersion, Long projectId, String content,
                      double score) {
    }

    public List<Hit> search(long memberId, float[] queryVector, Collection<String> skills, int limit) {
        Objects.requireNonNull(queryVector, "queryVector");
        return jdbc.sql("""
                        WITH vec AS (
                            SELECT id, 1 - (embedding <=> CAST(:q AS public.vector)) AS score
                            FROM rag.chunk
                            WHERE member_id = :m AND embedding_status = 'READY'
                            ORDER BY embedding <=> CAST(:q AS public.vector)
                            LIMIT :limit
                        ), kw AS (
                            SELECT id, 1.0 AS score
                            FROM rag.chunk
                            WHERE member_id = :m AND skills && CAST(:skills AS text[])
                            LIMIT :limit
                        )
                        SELECT c.id, c.chunk_type, c.source_id, c.source_version, c.project_id, c.content,
                               COALESCE(v.score, 0) * :vw + COALESCE(k.score, 0) * :kw AS score
                        FROM (SELECT id FROM vec UNION SELECT id FROM kw) ids
                        JOIN rag.chunk c ON c.id = ids.id AND c.member_id = :m
                        LEFT JOIN vec v ON v.id = c.id
                        LEFT JOIN kw k ON k.id = c.id
                        ORDER BY score DESC, c.id
                        LIMIT :limit
                        """)
                .param("q", Vectors.literal(queryVector))
                .param("m", memberId)
                .param("skills", skills.toArray(String[]::new))
                .param("vw", VECTOR_WEIGHT)
                .param("kw", KEYWORD_WEIGHT)
                .param("limit", limit)
                .query((rs, n) -> new Hit(rs.getLong(1), ChunkType.valueOf(rs.getString(2)), rs.getLong(3), rs.getInt(4),
                        (Long) rs.getObject(5), rs.getString(6), rs.getDouble(7)))
                .list();
    }
}
