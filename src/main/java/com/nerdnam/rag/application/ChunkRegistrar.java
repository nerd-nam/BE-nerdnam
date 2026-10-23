package com.nerdnam.rag.application;

import com.nerdnam.profile.application.ProfileChanged;
import com.nerdnam.project.domain.ProjectConfirmed;
import com.nerdnam.rag.infrastructure.ChunkSourceReader;
import com.nerdnam.rag.infrastructure.ChunkStore;
import com.nerdnam.rag.infrastructure.ChunkStore.Stored;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RAG 등록 (BE-10, F3). 원본이 바뀌면 같은 트랜잭션 안에서 청크를 다시 맞추고(텍스트만, PENDING),
 * 커밋한 뒤에 임베딩한다 ({@link ChunkEmbedder}).
 *
 * <p>이렇게 나눈 이유: 청크 교체는 원본 변경과 함께 커밋되거나 함께 취소되어야 하고(유실 없음),
 * AI 호출은 트랜잭션 밖에서 해야 한다. 임베딩이 실패하면 청크는 PENDING 으로 남고 주기 작업이 다시 시도한다.
 * 그래서 별도의 이벤트 발행 기록(Spring Modulith 등) 없이도 실패한 등록을 잃지 않는다.
 *
 * <p>맞추는 규칙: 원하는 청크와 저장된 청크를 (유형, 원본 id)로 짝지어, 내용·버전이 같으면 그대로 두고(다시 임베딩하지 않음),
 * 다르면 지우고 새로 넣고, 원본이 없어진 청크는 지운다.
 */
@Component
public class ChunkRegistrar {

    private final ChunkSourceReader sources;
    private final ChunkStore store;
    private final ChunkEmbedder embedder;

    public ChunkRegistrar(ChunkSourceReader sources, ChunkStore store, ChunkEmbedder embedder) {
        this.sources = sources;
        this.store = store;
        this.embedder = embedder;
    }

    /** 프로젝트 확정 (처음·재확정 같음): 그 프로젝트 청크를 새 확정 사례 기준으로 통째로 맞추고, 기술 사용처도 갱신 */
    @EventListener
    public void on(ProjectConfirmed event) {
        syncProject(event.memberId(), event.projectId());
        syncProfile(event.memberId());
        embedAfterCommit(event.memberId());
    }

    @EventListener
    public void on(ProfileChanged event) {
        syncProfile(event.memberId());
        embedAfterCommit(event.memberId());
    }

    void syncProfile(long memberId) {
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        List<ChunkDraft> desired = new ArrayList<>();
        sources.skills(memberId).forEach(s -> desired.add(ChunkTextBuilder.skill(s)));
        sources.careers(memberId).forEach(c -> desired.add(ChunkTextBuilder.career(c, now)));
        reconcile(memberId, store.profileChunks(memberId), desired);
    }

    /** 확정 상태가 아니면(재등록 중) 아무것도 하지 않는다 — 이전 확정본 청크를 다시 확정할 때까지 쓴다 (Q4) */
    void syncProject(long memberId, long projectId) {
        sources.confirmedProject(memberId, projectId).ifPresent(project -> {
            List<ChunkDraft> desired = new ArrayList<>();
            desired.add(ChunkTextBuilder.projectSummary(project));
            project.confirmedEpisodes().forEach(e -> desired.add(ChunkTextBuilder.episode(project, e)));
            reconcile(memberId, store.projectChunks(memberId, projectId), desired);
        });
    }

    private void reconcile(long memberId, List<Stored> existing, List<ChunkDraft> desired) {
        Map<String, Stored> byKey = new HashMap<>();
        existing.forEach(s -> byKey.put(key(s.type(), s.sourceId()), s));
        List<Long> toDelete = new ArrayList<>();
        List<ChunkDraft> toInsert = new ArrayList<>();
        for (ChunkDraft d : desired) {
            Stored current = byKey.remove(key(d.type(), d.sourceId()));
            boolean same = current != null && current.sourceVersion() == d.sourceVersion()
                    && Objects.equals(current.content(), d.content());
            if (!same) {
                if (current != null) {
                    toDelete.add(current.id());
                }
                toInsert.add(d);
            }
        }
        byKey.values().forEach(s -> toDelete.add(s.id()));   // 원본이 없어진 청크 (지운 기술·경력, 제외된 사례)
        store.delete(memberId, toDelete);
        toInsert.forEach(d -> store.insertPending(memberId, d));
    }

    private void embedAfterCommit(long memberId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    embedder.embedLater(memberId);
                }
            });
        } else {
            embedder.embedLater(memberId);
        }
    }

    private static String key(ChunkType type, long sourceId) {
        return type + ":" + sourceId;
    }
}
