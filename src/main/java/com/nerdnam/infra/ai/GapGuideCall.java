package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** POST /gap-guide (P6, ADR-0003) — 미충족·부분 충족 요건만. 실패해도 분석은 실패가 아니다 */
public final class GapGuideCall {

    private GapGuideCall() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(List<Requirement> requirements, List<String> tasks) {
    }

    /** @param adjacent 뽑히지 못한 후보 청크 (인접 경험) */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Requirement(Long id, String type, String text, String verdict, List<MatchCall.Candidate> adjacent) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(List<Guide> guides, String model, String promptVersion) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Guide(Long id, String advice, String avoid, List<Long> citedChunkIds) {
    }
}
