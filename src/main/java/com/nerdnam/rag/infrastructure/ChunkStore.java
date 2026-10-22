package com.nerdnam.rag.infrastructure;

import com.nerdnam.rag.application.ChunkDraft;
import com.nerdnam.rag.application.ChunkType;
import java.sql.Date;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** rag.chunk 읽기·쓰기 (SQL). 모든 메서드가 member_id 를 받는다 */
@Component
public class ChunkStore {

    private final JdbcClient jdbc;

    public ChunkStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Stored(long id, ChunkType type, long sourceId, int sourceVersion, String content) {
    }

    public record Pending(long id, String content) {
    }

    public List<Stored> profileChunks(long memberId) {
        return jdbc.sql("""
                        SELECT id, chunk_type, source_id, source_version, content FROM rag.chunk
                        WHERE member_id = :m AND chunk_type IN ('SKILL', 'CAREER')
                        """)
                .param("m", memberId)
                .query((rs, n) -> new Stored(rs.getLong(1), ChunkType.valueOf(rs.getString(2)), rs.getLong(3),
                        rs.getInt(4), rs.getString(5)))
                .list();
    }

    public List<Stored> projectChunks(long memberId, long projectId) {
        return jdbc.sql("""
                        SELECT id, chunk_type, source_id, source_version, content FROM rag.chunk
                        WHERE member_id = :m AND project_id = :p
                        """)
                .param("m", memberId)
                .param("p", projectId)
                .query((rs, n) -> new Stored(rs.getLong(1), ChunkType.valueOf(rs.getString(2)), rs.getLong(3),
                        rs.getInt(4), rs.getString(5)))
                .list();
    }

    public void delete(long memberId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        jdbc.sql("DELETE FROM rag.chunk WHERE member_id = :m AND id IN (:ids)")
                .param("m", memberId)
                .param("ids", ids)
                .update();
    }

    /** 벡터 없이 PENDING 으로 저장한다. 임베딩은 커밋 뒤에 따로 (GPU 가 꺼져 있어도 확정은 된다) */
    public void insertPending(long memberId, ChunkDraft c) {
        jdbc.sql("""
                        INSERT INTO rag.chunk (member_id, chunk_type, source_id, source_version, project_id, content,
                                               skills, period_start, period_end, months, has_metric)
                        VALUES (:m, :type, :sourceId, :sourceVersion, :projectId, :content,
                                :skills, :periodStart, :periodEnd, :months, :hasMetric)
                        """)
                .param("m", memberId)
                .param("type", c.type().name())
                .param("sourceId", c.sourceId())
                .param("sourceVersion", c.sourceVersion())
                .param("projectId", c.projectId())
                .param("content", c.content())
                .param("skills", c.skills().toArray(String[]::new))
                .param("periodStart", date(c.periodStart()))
                .param("periodEnd", date(c.periodEnd()))
                .param("months", c.months())
                .param("hasMetric", c.hasMetric())
                .update();
    }

    public List<Pending> pending(long memberId, int limit) {
        return jdbc.sql("""
                        SELECT id, content FROM rag.chunk
                        WHERE member_id = :m AND embedding_status = 'PENDING'
                        ORDER BY id LIMIT :limit
                        """)
                .param("m", memberId)
                .param("limit", limit)
                .query((rs, n) -> new Pending(rs.getLong(1), rs.getString(2)))
                .list();
    }

    public List<Long> membersWithPending(int limit) {
        return jdbc.sql("""
                        SELECT member_id FROM rag.chunk WHERE embedding_status = 'PENDING'
                        GROUP BY member_id ORDER BY min(created_at) LIMIT :limit
                        """)
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    /**
     * 임베딩 결과 저장. 그사이 청크가 지워졌거나 내용이 바뀌었으면(다시 PENDING 으로 새로 들어감) 아무것도 하지 않는다.
     */
    public boolean markReady(long chunkId, String content, float[] vector, String model) {
        return jdbc.sql("""
                        UPDATE rag.chunk
                        SET embedding = CAST(:vector AS public.vector), embedding_status = 'READY', embedding_model = :model
                        WHERE id = :id AND embedding_status = 'PENDING' AND content = :content
                        """)
                .param("vector", Vectors.literal(vector))
                .param("model", model)
                .param("id", chunkId)
                .param("content", content)
                .update() == 1;
    }

    private static Date date(YearMonth month) {
        return month == null ? null : Date.valueOf(month.atDay(1));
    }
}
