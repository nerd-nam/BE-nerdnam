package com.nerdnam.infra.ai;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /guide (P4·P5) — 탭 하나의 작성 방향. AI 서버는 SSE 로 흘려 보내고, API 는 끝까지 받아 모은 뒤 저장한다
 * (웹으로 중계하지 않는다, 기획서 v1.11).
 */
public final class GuideCall {

    private GuideCall() {
    }

    /** @param tab motivation / self_introduction / portfolio / career_description / image_prompt */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(String tab, String company, String position, String companyIntro, List<String> tasks,
                          List<Requirement> requirements, String feedback) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Requirement(Long id, String type, String text, String verdict, String reason,
                              List<MatchCall.Candidate> evidence, GapGuide gapGuide) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GapGuide(String advice, String avoid) {
    }

    /** content 의 모양은 탭마다 다르고 AI 계약이 정한다. API 는 통째로 jsonb 에 저장한다 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(Map<String, Object> content, List<Long> citedChunkIds, String model, String promptVersion) {
    }
}
