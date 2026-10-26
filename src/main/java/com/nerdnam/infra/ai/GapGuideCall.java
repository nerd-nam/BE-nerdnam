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

        /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
        public Request {
            tasks = tasks == null ? List.of() : tasks;
        }
    }

    /** @param adjacent 뽑히지 못한 후보 청크 (인접 경험) */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Requirement(Long id, String type, String text, String verdict, List<MatchCall.Candidate> adjacent) {

        /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
        public Requirement {
            adjacent = adjacent == null ? List.of() : adjacent;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(List<Guide> guides, String model, String promptVersion) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Guide(Long id, String advice, String avoid, List<Long> citedChunkIds) {
    }
}
