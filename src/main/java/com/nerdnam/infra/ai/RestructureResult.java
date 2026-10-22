package com.nerdnam.infra.ai;

import java.util.List;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** POST /restructure 응답. episode 모드면 episodes 에 고친 사례 1개 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RestructureResult(List<AiEpisode> episodes, String model, String promptVersion) {
}
