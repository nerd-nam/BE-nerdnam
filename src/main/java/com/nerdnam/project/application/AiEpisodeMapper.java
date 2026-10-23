package com.nerdnam.project.application;

import com.nerdnam.infra.ai.AiEpisode;
import com.nerdnam.project.domain.Clarification;
import com.nerdnam.project.domain.EpisodeContent;
import com.nerdnam.project.domain.Metric;
import com.nerdnam.project.domain.SourceSpan;
import java.util.ArrayList;
import java.util.List;

/** AI 계약의 사례 모양 ↔ 도메인 EpisodeContent (부패 방지 계층의 project 쪽 번역) */
final class AiEpisodeMapper {

    private AiEpisodeMapper() {
    }

    static AiEpisode toAi(EpisodeContent c) {
        return new AiEpisode(c.title(), c.role(), c.problem(), c.solution(), c.result(), c.skills(),
                c.metrics().stream().map(m -> new AiEpisode.Metric(m.name(), m.before(), m.after(), m.source())).toList(),
                c.sourceSpans().stream().map(s -> List.of(s.start(), s.end())).toList(),
                c.clarifications().stream()
                        .map(q -> new AiEpisode.Clarification(q.key(), q.field(), q.question(), q.example()))
                        .toList());
    }

    /**
     * AI 결과를 도메인 값으로. 모양이 틀린 원문 구간(길이가 2가 아님, 원문 밖)은 버리고,
     * 확인 질문 key 가 비어 있거나 겹치면 q1, q2 … 로 붙인다 (답하기·빼고 진행에 key 가 필요하다).
     */
    static EpisodeContent toDomain(AiEpisode e, int sourceLength) {
        List<SourceSpan> spans = new ArrayList<>();
        if (e.sourceSpans() != null) {
            for (List<Integer> span : e.sourceSpans()) {
                if (span != null && span.size() == 2 && span.get(0) != null && span.get(1) != null
                        && 0 <= span.get(0) && span.get(0) <= span.get(1) && span.get(1) <= sourceLength) {
                    spans.add(new SourceSpan(span.get(0), span.get(1)));
                }
            }
        }
        List<Clarification> questions = new ArrayList<>();
        if (e.clarifications() != null) {
            int n = 1;
            for (AiEpisode.Clarification q : e.clarifications()) {
                if (q == null || q.question() == null || q.question().isBlank()) {
                    continue;
                }
                String key = q.key();
                String candidate = key;
                if (candidate == null || candidate.isBlank() || questions.stream().anyMatch(x -> x.key().equals(candidate))) {
                    key = "q" + n;
                }
                questions.add(new Clarification(key, q.field(), q.question().strip(), q.example()));
                n++;
            }
        }
        List<Metric> metrics = e.metrics() == null ? List.of() : e.metrics().stream()
                .filter(m -> m != null && m.name() != null)
                .map(m -> new Metric(m.name(), m.before(), m.after(), m.source()))
                .toList();
        String title = e.title() == null || e.title().isBlank() ? "제목 없는 사례" : e.title();
        return new EpisodeContent(title, e.role(), e.problem(), e.solution(), e.result(), e.skills(), metrics, spans,
                questions);
    }
}
