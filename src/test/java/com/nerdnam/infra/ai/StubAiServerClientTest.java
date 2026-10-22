package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubAiServerClientTest {

    private final StubAiServerClient stub = new StubAiServerClient();

    @Test
    void 같은_텍스트는_항상_같은_768차원_단위_벡터다() {
        float[] first = stub.embed(List.of("결제 승인 API"), EmbedPurpose.DOCUMENT).vectors().getFirst();
        float[] second = stub.embed(List.of("결제 승인 API"), EmbedPurpose.DOCUMENT).vectors().getFirst();

        assertThat(first).hasSize(StubAiServerClient.DIMENSIONS).containsExactly(second);
        double norm = 0;
        for (float v : first) {
            norm += v * v;
        }
        assertThat(norm).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void 용도가_다르면_다른_벡터다() {
        float[] document = stub.embed(List.of("Kafka"), EmbedPurpose.DOCUMENT).vectors().getFirst();
        float[] query = stub.embed(List.of("Kafka"), EmbedPurpose.QUERY).vectors().getFirst();

        assertThat(document).isNotEqualTo(query);
    }

    @Test
    void 스텁은_항상_준비_상태다() {
        assertThat(stub.health().ready()).isTrue();
    }
}
