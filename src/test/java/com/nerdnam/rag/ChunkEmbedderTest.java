package com.nerdnam.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.AiServerHealth;
import com.nerdnam.infra.ai.AiServerUnavailableException;
import com.nerdnam.infra.ai.EmbedPurpose;
import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.rag.application.ChunkEmbedder;
import com.nerdnam.rag.infrastructure.ChunkStore;
import com.nerdnam.rag.infrastructure.ChunkStore.Pending;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;

/** 임베딩 실패·재시도 규칙 (DB·AI 서버 없이 가짜 객체로) */
class ChunkEmbedderTest {

    private final ChunkStore store = mock(ChunkStore.class);
    private final AiServerClient ai = mock(AiServerClient.class);
    private final ChunkEmbedder embedder = new ChunkEmbedder(store, ai, new SyncTaskExecutor());

    @Test
    void GPU_서버가_꺼져_있으면_PENDING으로_남긴다() {
        when(store.pending(7L, 32)).thenReturn(List.of(new Pending(1, "청크")));
        when(ai.embed(any(), eq(EmbedPurpose.DOCUMENT))).thenThrow(
                new AiServerUnavailableException(AiServerUnavailableException.Reason.GPU_UNAVAILABLE, "embed", null));

        int done = embedder.embedPending(7L);

        assertThat(done).isZero();
        verify(store, never()).markReady(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void 받은_벡터를_청크마다_저장한다() {
        when(store.pending(7L, 32)).thenReturn(List.of(new Pending(1, "a"), new Pending(2, "b")), List.of());
        when(ai.embed(List.of("a", "b"), EmbedPurpose.DOCUMENT))
                .thenReturn(new Embeddings(List.of(new float[] {1}, new float[] {2}), "model:768"));
        when(store.markReady(anyLong(), anyString(), any(), anyString())).thenReturn(true);

        assertThat(embedder.embedPending(7L)).isEqualTo(2);
        verify(store).markReady(eq(2L), eq("b"), any(), eq("model:768"));
    }

    @Test
    void AI_서버가_준비되지_않았으면_재시도하지_않는다() {
        when(store.membersWithPending(anyInt())).thenReturn(List.of(7L));
        when(ai.health()).thenReturn(AiServerHealth.unreachable());

        embedder.retryPending();

        verify(store, never()).pending(anyLong(), anyInt());
    }
}
