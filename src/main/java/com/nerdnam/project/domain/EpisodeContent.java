package com.nerdnam.project.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 사례 내용 — [문제]-[해결]-[결과]와 메타정보를 묶은 값 객체 (기획서 4.3).
 * 바뀌면 새 객체(= 새 버전)를 만든다. 목록은 모두 바꿀 수 없는 복사본이다.
 */
public record EpisodeContent(
        String title,
        String role,
        String problem,
        String solution,
        String result,
        List<String> skills,
        List<Metric> metrics,
        List<SourceSpan> sourceSpans,
        List<Clarification> clarifications) {

    public static final int TITLE_MAX = 100;
    public static final int ROLE_MAX = 200;

    public EpisodeContent {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "사례 제목을 입력해 주세요.");
        }
        title = cut(title.strip(), TITLE_MAX);
        role = role == null || role.isBlank() ? null : cut(role.strip(), ROLE_MAX);
        problem = Objects.requireNonNullElse(problem, "").strip();
        solution = Objects.requireNonNullElse(solution, "").strip();
        result = Objects.requireNonNullElse(result, "").strip();
        skills = skills == null ? List.of() : skills.stream().filter(Objects::nonNull).map(String::strip)
                .filter(s -> !s.isEmpty()).distinct().toList();
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        sourceSpans = sourceSpans == null ? List.of() : List.copyOf(sourceSpans);
        clarifications = clarifications == null ? List.of() : List.copyOf(clarifications);
    }

    /** P2: 확인 필요 질문이 남아 있으면 확정할 수 없다 */
    public boolean hasOpenClarifications() {
        return !clarifications.isEmpty();
    }

    public boolean hasMetric() {
        return !metrics.isEmpty();
    }

    public Clarification clarification(String key) {
        return clarifications.stream()
                .filter(c -> Objects.equals(c.key(), key))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "확인 질문을 찾을 수 없어요."));
    }

    /** "빼고 진행" (Q10) — 그 질문만 뺀 내용 */
    public EpisodeContent withoutClarification(String key) {
        clarification(key);   // 없으면 404
        return new EpisodeContent(title, role, problem, solution, result, skills, metrics, sourceSpans,
                clarifications.stream().filter(c -> !Objects.equals(c.key(), key)).toList());
    }

    /** 직접 편집 — 사용 기술·원문 구간·확인 질문은 그대로 둔다 (C-02 S05 4) */
    public EpisodeContent edited(EpisodeEdit edit) {
        return new EpisodeContent(edit.title(), edit.role(), edit.problem(), edit.solution(), edit.result(),
                skills, edit.metrics() == null ? metrics : edit.metrics(), sourceSpans, clarifications);
    }

    /**
     * P9: AI 가 돌려준 사용 기술 중 프로젝트 사용 기술에 있는 것만 남기고, 이름은 프로젝트 쪽 표준 이름으로 맞춘다.
     * (대소문자·공백만 다른 "spring boot" → "Spring Boot")
     */
    public EpisodeContent limitSkillsTo(Collection<String> allowedSkillNames) {
        Map<String, String> allowed = new LinkedHashMap<>();
        allowedSkillNames.forEach(name -> allowed.putIfAbsent(key(name), name));
        List<String> kept = skills.stream().map(s -> allowed.get(key(s))).filter(Objects::nonNull).distinct().toList();
        return new EpisodeContent(title, role, problem, solution, result, kept, metrics, sourceSpans, clarifications);
    }

    /** 라이브러리 한 줄 요약 "문제 → 해결 → 결과" (C-02 S06 2: 서버가 잘라 붙인다, AI 아님) */
    public String summaryLine(int eachMax) {
        return cut(firstLine(problem), eachMax) + " → " + cut(firstLine(solution), eachMax) + " → " + cut(firstLine(result), eachMax);
    }

    private static String firstLine(String text) {
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
