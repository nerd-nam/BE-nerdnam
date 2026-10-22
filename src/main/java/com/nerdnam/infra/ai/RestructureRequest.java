package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * POST /restructure 요청 (P1, 기획서 5.5). AI 서버는 상태가 없으므로 필요한 맥락을 모두 보낸다.
 *
 * @param mode             split = 나누기·다시 나누기 / episode = 사례 하나 고치기(수정 요청·확인 답변)
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

    public static final String MODE_SPLIT = "split";
    public static final String MODE_EPISODE = "episode";

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Answer(String question, String answer) {
    }
}
