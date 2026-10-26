package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * /restructure 의 사례 한 개 (기획서 4.3 에피소드 모양, 계약 C-04).
 *
 * <p>계약에서 {@code role}·목록 필드는 <b>"없으면 기본값"이지만 null 은 허용하지 않습니다</b>
 * ({@code role: string, default: ''}). 도메인은 빈 역할을 null 로 두므로(역할 없는 사례) 그대로
 * 보내면 실제 AI 서버가 422 로 거절합니다 — 2026-10-26 실서버 연결에서 확인했고, 스텁은 받아 줘서
 * 그때까지 드러나지 않았습니다. 그래서 여기서 계약의 기본값으로 바꿉니다.
 * {@code ApiAiContractTest.보내는_값을_모를_때_계약의_기본값으로_채운다} 가 고정합니다.
 */
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

    /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 (아래 클래스 설명) */
    public AiEpisode {
        role = role == null ? "" : role;
        skills = skills == null ? List.of() : skills;
        metrics = metrics == null ? List.of() : metrics;
        sourceSpans = sourceSpans == null ? List.of() : sourceSpans;
        clarifications = clarifications == null ? List.of() : clarifications;
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Metric(String name, String before, String after, String source) {

        /** {@code source} 는 근거 위치다. 모르면 원문으로 본다 (계약 기본값) */
        public Metric {
            source = source == null ? "raw_text" : source;
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Clarification(String key, String field, String question, String example) {
    }
}
