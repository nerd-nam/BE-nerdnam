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
import com.nerdnam.infra.ai.EmbedItem;
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
        when(store.pending(7L, 32)).thenReturn(List.of(new Pending(1, "제목", "청크")));
        when(ai.embed(any(), eq(EmbedPurpose.DOCUMENT))).thenThrow(
                new AiServerUnavailableException(AiServerUnavailableException.Reason.GPU_UNAVAILABLE, "embed", null));

        int done = embedder.embedPending(7L);

        assertThat(done).isZero();
        verify(store, never()).markReady(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void 받은_벡터를_청크마다_저장하고_제목도_같이_보낸다() {
        when(store.pending(7L, 32))
                .thenReturn(List.of(new Pending(1, "Redis", "a"), new Pending(2, null, "b")), List.of());
        when(ai.embed(List.of(new EmbedItem("a", "Redis"), new EmbedItem("b", null)), EmbedPurpose.DOCUMENT))
                .thenReturn(new Embeddings(List.of(vector(1), vector(2)), "model:1024"));
        when(store.markReady(anyLong(), anyString(), any(), anyString())).thenReturn(true);

        assertThat(embedder.embedPending(7L)).isEqualTo(2);
        verify(store).markReady(eq(2L), eq("b"), any(), eq("model:1024"));
    }

    /**
     * 차원이 DB 열과 다른 벡터는 저장하지 않는다 (이슈 #31). 넣으려 하면 pgvector 가 SQL 에서
     * "different vector dimensions" 로 거절해 작업이 {@code internal} 로만 실패하고 원인이 안 보인다.
     * 임베딩 모델을 바꾸면서 마이그레이션을 빠뜨리면 이 상태가 된다.
     */
    @Test
    void 차원이_다른_벡터는_저장하지_않고_대기로_둔다() {
        when(store.pending(7L, 32)).thenReturn(List.of(new Pending(1, "Redis", "a")), List.of());
        float[] 짧은_벡터 = new float[Embeddings.DIMENSIONS - 1];
        when(ai.embed(List.of(new EmbedItem("a", "Redis")), EmbedPurpose.DOCUMENT))
                .thenReturn(new Embeddings(List.of(짧은_벡터), "amazon.titan-embed-text-v2:0:512"));

        assertThat(embedder.embedPending(7L)).isZero();
        verify(store, never()).markReady(anyLong(), anyString(), any(), anyString());
    }

    /** DB 열과 같은 차원의 벡터. 첫 칸만 값을 넣는다 (내용은 이 테스트에서 중요하지 않다) */
    private static float[] vector(float first) {
        float[] v = new float[Embeddings.DIMENSIONS];
        v[0] = first;
        return v;
    }

    /** AI 서버가 가짜 응답(stub) 모드면 벡터를 저장하지 않고 PENDING 으로 둔다 (기획서 5.5) */
    @Test
    void 가짜_응답_벡터는_저장하지_않는다() {
        when(store.pending(7L, 32)).thenReturn(List.of(new Pending(1, "Redis", "a")));
        when(ai.embed(any(), eq(EmbedPurpose.DOCUMENT)))
                .thenReturn(new Embeddings(List.of(new float[] {1}), Embeddings.STUB_MODEL));

        assertThat(embedder.embedPending(7L)).isZero();
        verify(store, never()).markReady(anyLong(), anyString(), any(), anyString());
    }

    @Test
    void AI_서버가_준비되지_않았으면_재시도하지_않는다() {
        when(store.membersWithPending(anyInt())).thenReturn(List.of(7L));
        when(ai.health()).thenReturn(AiServerHealth.unreachable());

        embedder.retryPending();

        verify(store, never()).pending(anyLong(), anyInt());
    }

    @Test
    void 생성_모델이_꺼져_있어도_임베딩이_준비됐으면_재처리한다() {
        when(store.membersWithPending(anyInt())).thenReturn(List.of(7L));
        when(ai.health()).thenReturn(new AiServerHealth(false, java.util.Map.of("llm", "error", "embedding", "ready")));
        when(store.pending(7L, 32)).thenReturn(List.of());
        embedder.retryPending();
        verify(store).pending(7L, 32);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"loading", "error", "stub"})
    void 임베딩이_준비되지_않으면_재처리하지_않는다(String state) {
        when(store.membersWithPending(anyInt())).thenReturn(List.of(7L));
        when(ai.health()).thenReturn(new AiServerHealth(false, java.util.Map.of("llm", "ready", "embedding", state)));
        embedder.retryPending();
        verify(store, never()).pending(anyLong(), anyInt());
    }
}
