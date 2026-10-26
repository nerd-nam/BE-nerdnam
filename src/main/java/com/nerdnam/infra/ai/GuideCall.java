package com.nerdnam.infra.ai;

import java.util.List;
import java.util.Map;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /guide (P4·P5) — 탭 하나의 작성 방향을 JSON 한 덩어리로 받아 저장한다 (기획서 v1.23).
 */
public final class GuideCall {

    private GuideCall() {
    }

    /** @param tab motive / intro / portfolio / career / image */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Request(String tab, String company, String position, String companyIntro, List<String> tasks,
                          List<Requirement> requirements, String feedback) {

        /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
        public Request {
            company = company == null ? "" : company;
            position = position == null ? "" : position;
            companyIntro = companyIntro == null ? "" : companyIntro;
            tasks = tasks == null ? List.of() : tasks;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Requirement(Long id, String type, String text, String verdict, String reason,
                              List<MatchCall.Candidate> evidence, GapGuide gapGuide) {

        /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
        public Requirement {
            evidence = evidence == null ? List.of() : evidence;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record GapGuide(String advice, String avoid) {
    }

    /** content 의 모양은 탭마다 다르고 AI 계약이 정한다. API 는 통째로 jsonb 에 저장한다 */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Result(Map<String, Object> content, List<Long> citedChunkIds, String model, String promptVersion) {
    }
}
