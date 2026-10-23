package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /requirements/extract (P2). 줄마다 기술명 원문과 최소 연차만 뽑는다. 문구는 바꾸지 않는다 (F4).
 */
public final class RequirementExtraction {

    private RequirementExtraction() {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(List<Line> lines) {
    }

    /** @param type required / preferred */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Line(Long id, String type, String text) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(List<Extracted> lines, String model, String promptVersion) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Extracted(Long id, List<String> skillsRaw, Integer minYears) {
    }
}
