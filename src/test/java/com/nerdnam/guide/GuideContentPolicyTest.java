package com.nerdnam.guide;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.guide.domain.GuideContentPolicy;
import com.nerdnam.guide.domain.GuideContentPolicy.Cleaned;
import com.nerdnam.guide.domain.GuideTab;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 작성 방향 저장 전 다듬기: 인용 출처 소유권, 포트폴리오 페이지 순서·종류 */
class GuideContentPolicyTest {

    @Test
    void 이_분석의_근거가_아닌_청크_인용은_뺀다() {
        Map<String, Object> content = Map.of("sections", List.of(
                Map.of("title", "도입", "sources", List.of(1, 999)),
                Map.of("title", "본문", "sources", List.of(2))));

        Cleaned cleaned = GuideContentPolicy.clean(GuideTab.MOTIVATION, content, List.of(1L, 2L, 999L, 1L), Set.of(1L, 2L));

        assertThat(cleaned.citedChunkIds()).containsExactly(1L, 2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sections = (List<Map<String, Object>>) cleaned.content().get("sections");
        assertThat(sections.getFirst().get("sources")).isEqualTo(List.of(1L));
        assertThat(cleaned.droppedCitations()).isEqualTo(2);
    }

    @Test
    void 포트폴리오_페이지는_order_순이고_모르는_kind는_text() {
        Map<String, Object> content = Map.of("pages", List.of(
                Map.of("order", 3, "kind", "video"),
                Map.of("order", 1, "kind", "cover"),
                Map.of("order", 2, "kind", "diagram")));

        Cleaned cleaned = GuideContentPolicy.clean(GuideTab.PORTFOLIO, content, List.of(), Set.of());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pages = (List<Map<String, Object>>) cleaned.content().get("pages");
        assertThat(pages).extracting(p -> p.get("order")).containsExactly(1, 2, 3);
        assertThat(pages).extracting(p -> p.get("kind")).containsExactly("cover", "diagram", "text");
    }

    @Test
    void 탭_이름은_웹_주소_이름과_enum_이름_둘_다_받는다() {
        assertThat(GuideTab.parse("motive")).isEqualTo(GuideTab.MOTIVATION);
        assertThat(GuideTab.parse("SELF_INTRODUCTION")).isEqualTo(GuideTab.SELF_INTRODUCTION);
    }

    /** 기획서 v1.20: AI 서버 /guide 의 tab 은 웹 주소와 같은 값만 받는다 (다르면 422) */
    @Test
    void AI_서버에_보내는_탭_이름은_웹_주소_이름과_같다_v1_20() {
        assertThat(GuideTab.values()).extracting(GuideTab::aiName)
                .containsExactly("motive", "intro", "portfolio", "career", "image");
    }
}
