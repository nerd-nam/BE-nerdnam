package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /requirements/extract (P2). 줄마다 기술명 원문·최소 연차·약어 확장어를 뽑는다. 문구는 바꾸지 않는다 (F4).
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
    public record Result(List<Extracted> lines, String model, String promptVersion, AiUsage usage) {
    }

    /**
     * @param skillsRaw  줄에 쓰인 기술명 원문 (정규화는 API 가 한다)
     * @param expansions 줄에 쓰인 약어의 풀네임·같은 뜻의 다른 표기, 줄마다 최대 3개 (관련어는 넣지 않는다).
     *                   없으면 빈 목록이다. API 가 검색 질의와 키워드 검색에 쓴다 (기획서 5.4, 계약 v0.3.0)
     * @param minYears   "N년 이상" 같은 표현에서 뽑은 최소 연차. 없으면 null
     */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Extracted(Long id, List<String> skillsRaw, List<String> expansions, Integer minYears) {

        public List<String> expansions() {
            return expansions == null ? List.of() : expansions;
        }
    }
}
