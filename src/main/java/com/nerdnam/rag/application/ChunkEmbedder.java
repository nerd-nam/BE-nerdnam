package com.nerdnam.rag.application;

import com.nerdnam.infra.ai.AiCallContext;
import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.AiServerUnavailableException;
import com.nerdnam.infra.ai.EmbedItem;
import com.nerdnam.infra.ai.EmbedPurpose;
import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.rag.infrastructure.ChunkStore;
import com.nerdnam.rag.infrastructure.ChunkStore.Pending;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PENDING 청크를 AI 서버로 임베딩해 READY 로 바꾼다. 트랜잭션 없이 돈다 (각 UPDATE 가 바로 커밋).
 * GPU 서버가 꺼져 있으면 PENDING 으로 두고, 5분마다 AI 서버가 준비됐는지 보고 다시 시도한다 (기획서 8장).
 * AI 서버가 가짜 응답(stub) 모드면({@code embedding_model = "stub"}) 그 벡터는 저장하지 않고 역시 PENDING 으로 둔다 (기획서 5.5).
 */
@Component
public class ChunkEmbedder {

    private static final Logger log = LoggerFactory.getLogger(ChunkEmbedder.class);
    private static final int BATCH = 32;
    private static final int MAX_BATCHES_PER_RUN = 20;

    private final ChunkStore store;
    private final AiServerClient ai;
    private final TaskExecutor executor;

    public ChunkEmbedder(ChunkStore store, AiServerClient ai, @Qualifier("aiJobExecutor") TaskExecutor executor) {
        this.store = store;
        this.ai = ai;
        this.executor = executor;
    }

    /** 커밋 뒤에 부른다. 별도 스레드에서 임베딩한다 (테스트 설정에서는 바로) */
    public void embedLater(long memberId) {
        try {
            executor.execute(() -> embedPending(memberId));
        } catch (RuntimeException e) {
            log.warn("Could not schedule embedding for member {}; the retry job will pick it up", memberId, e);
        }
    }

    /** @return READY 로 바꾼 청크 수 */
    public int embedPending(long memberId) {
        int done = 0;
        try {
            for (int round = 0; round < MAX_BATCHES_PER_RUN; round++) {
                List<Pending> batch = store.pending(memberId, BATCH);
                if (batch.isEmpty()) {
                    break;
                }
                List<EmbedItem> items = batch.stream().map(p -> new EmbedItem(p.content(), p.title())).toList();
                Embeddings embeddings = AiCallContext.with(new AiCallContext(memberId, "CHUNK", null),
                        () -> ai.embed(items, EmbedPurpose.DOCUMENT));
                if (embeddings.stub()) {
                    log.info("AI server returned stub embeddings; chunks of member {} stay PENDING", memberId);
                    break;
                }
                int marked = 0;
                for (int i = 0; i < batch.size(); i++) {
                    if (store.markReady(batch.get(i).id(), batch.get(i).content(), embeddings.vectors().get(i),
                            embeddings.embeddingModel())) {
                        marked++;
                    }
                }
                done += marked;
                if (marked == 0) {
                    break;   // 모두 그사이 바뀌었다 — 다음 시도에 맡긴다
                }
            }
        } catch (AiServerUnavailableException e) {
            log.info("Embedding postponed for member {} ({}); chunks stay PENDING", memberId, e.reason());
        } catch (RuntimeException e) {
            log.error("Embedding failed for member {}", memberId, e);
        }
        return done;
    }

    /** 남은 PENDING 청크 다시 시도 (GPU 서버가 켜져 있을 때만) */
    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void retryPending() {
        List<Long> members = store.membersWithPending(50);
        if (members.isEmpty() || !ai.health().ready()) {
            return;
        }
        members.forEach(this::embedPending);
    }
}
