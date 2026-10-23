package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** /restructure 의 사례 한 개 (기획서 4.3 에피소드 모양, 계약 C-04 전 초안) */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiEpisode(
        String title,
        String role,
        String problem,
        String solution,
        String result,
        List<String> skills,
        List<Metric> metrics,
        List<List<Integer>> sourceSpans,
        List<Clarification> clarifications) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Metric(String name, String before, String after, String source) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Clarification(String key, String field, String question, String example) {
    }
}
