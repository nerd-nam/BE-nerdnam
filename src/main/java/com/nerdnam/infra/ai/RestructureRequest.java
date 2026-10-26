package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /restructure 요청 (P1, 기획서 5.5). AI 서버는 상태가 없으므로 필요한 맥락을 모두 보낸다.
 *
 * @param mode             initial = 첫 나누기(원문 수정 후 재구성 포함) / split = 다시 나누기(피드백 필수) /
 *                         episode = 사례 하나 고치기(수정 요청·확인 답변). 기획서 v1.20 입력 규칙 — 어기면 422
 * @param sourceText       원문 + 보충 답변 (source_spans 의 기준)
 * @param previousEpisodes 재등록 중이면 이전 확정 사례 (맥락)
 * @param currentEpisodes  다시 나누기면 지금 검토 중인 사례 (피드백의 대상)
 * @param targetEpisode    episode 모드에서 고칠 사례
 * @param answer           확인 질문 답변 (episode 모드, 답변일 때)
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RestructureRequest(
        String mode,
        String title,
        String sourceText,
        List<String> skills,
        List<AiEpisode> previousEpisodes,
        List<AiEpisode> currentEpisodes,
        AiEpisode targetEpisode,
        String feedback,
        Answer answer) {

    /** 첫 나누기 (S04 "재구성하기"). 원문을 고쳐 다시 나누는 것도 이 모드다 */
    /** 계약에 기본값이 있는 필드는 null 로 보내지 않는다 ({@link AiEpisode} 설명) */
    public RestructureRequest {
        title = title == null ? "" : title;
        skills = skills == null ? List.of() : skills;
        previousEpisodes = previousEpisodes == null ? List.of() : previousEpisodes;
        currentEpisodes = currentEpisodes == null ? List.of() : currentEpisodes;
    }

    public static final String MODE_INITIAL = "initial";
    /** 다시 나누기 (S05). 비어 있지 않은 feedback 이 필요하다 (기획서 F2·v1.20) */
    public static final String MODE_SPLIT = "split";
    /** 사례 하나 고치기 (수정 요청·확인 답변) */
    public static final String MODE_EPISODE = "episode";

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Answer(String question, String answer) {
    }
}
