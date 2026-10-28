package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** POST /match (P3) — 요건 묶음 + 요건별 후보 청크 → 요건별 판정·근거 청크 id·이유 */
public final class MatchCall {

    private MatchCall() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(List<Requirement> requirements) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Requirement(Long id, String type, String text, List<String> skills, Integer minYears,
                              List<Candidate> candidates) {

        /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
        public Requirement {
            skills = skills == null ? List.of() : skills;
        }
    }

    /** AI 서버는 사용자 정보를 받지 않는다. 청크는 id 와 텍스트만 (기획서 5.5) */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(Long chunkId, String chunkType, String text) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(List<Judgement> results, String model, String promptVersion, AiUsage usage) {
    }

    /** @param verdict met / partial / unmet */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Judgement(Long id, String verdict, List<Long> evidenceChunkIds, String reason) {
    }
}
