package com.nerdnam.guide.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 가 만든 탭 내용을 저장하기 전에 다듬는다. 내용 모양은 AI 계약이 정하므로 도메인 규칙은 두지 않고 두 가지만 본다:
 * <ul>
 *   <li><b>인용 출처 소유권</b> (기획서 5.4-6): 인용한 청크 id 는 이 분석의 근거·갭 가이드 인용 안의 것만 남긴다.
 *       내용 안 어디에 있든 {@code sources} 목록의 숫자도 같은 기준으로 거른다.</li>
 *   <li><b>포트폴리오 페이지</b> (BE-14): {@code pages}를 order 순으로 정렬하고 kind 가 cover·text·diagram·chart 가 아니면 text 로.
 *       시각자료({@code visual})는 만들지 않는다 — 차트·도식은 서비스가 만들지 않고 안내만 준다 (D-1 결정, 2026-10-23).</li>
 * </ul>
 */
public final class GuideContentPolicy {

    private static final Set<String> PAGE_KINDS = Set.of("cover", "text", "diagram", "chart");

    private GuideContentPolicy() {
    }

    public record Cleaned(Map<String, Object> content, List<Long> citedChunkIds, int droppedCitations) {
    }

    public static Cleaned clean(GuideTab tab, Map<String, Object> content, Collection<Long> cited, Set<Long> allowed) {
        int[] dropped = {0};
        List<Long> keptCited = new ArrayList<>();
        if (cited != null) {
            for (Long id : cited) {
                if (id != null && allowed.contains(id)) {
                    if (!keptCited.contains(id)) {
                        keptCited.add(id);
                    }
                } else {
                    dropped[0]++;
                }
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> cleaned = (Map<String, Object>) walk(content == null ? Map.of() : content, allowed, dropped, false);
        if (tab == GuideTab.PORTFOLIO && cleaned.get("pages") instanceof List<?> pages) {
            cleaned.put("pages", pages(pages));
        }
        return new Cleaned(cleaned, keptCited, dropped[0]);
    }

    private static Object walk(Object node, Set<Long> allowed, int[] dropped, boolean inSources) {
        if (node instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), walk(v, allowed, dropped, "sources".equals(k))));
            return copy;
        }
        if (node instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                if (inSources && item instanceof Number n) {
                    if (allowed.contains(n.longValue())) {
                        copy.add(n.longValue());
                    } else {
                        dropped[0]++;
                    }
                } else {
                    copy.add(walk(item, allowed, dropped, false));
                }
            }
            return copy;
        }
        return node;
    }

    private static List<Object> pages(List<?> pages) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object page : pages) {
            if (page instanceof Map<?, ?> m) {
                Map<String, Object> copy = new LinkedHashMap<>();
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                Object kind = copy.get("kind");
                if (!(kind instanceof String s) || !PAGE_KINDS.contains(s)) {
                    copy.put("kind", "text");
                }
                list.add(copy);
            }
        }
        list.sort(Comparator.comparingInt(p -> p.get("order") instanceof Number n ? n.intValue() : Integer.MAX_VALUE));
        return new ArrayList<Object>(list);
    }
}
