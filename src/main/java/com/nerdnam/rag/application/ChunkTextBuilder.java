package com.nerdnam.rag.application;

import com.nerdnam.rag.application.ChunkSources.CareerSource;
import com.nerdnam.rag.application.ChunkSources.EpisodeSource;
import com.nerdnam.rag.application.ChunkSources.ProjectSource;
import com.nerdnam.rag.application.ChunkSources.SkillSource;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 청크 텍스트 구성 규칙 (기획서 4.4). 임베딩·판정·인용 품질이 이 텍스트에 달려 있으므로
 * 모양을 단위 테스트로 고정한다 (ChunkTextBuilderTest). 바꾸면 모든 청크가 다시 임베딩된다(내용이 달라지므로).
 */
public final class ChunkTextBuilder {

    private static final Map<String, String> CATEGORY = Map.of(
            "LANGUAGE", "언어", "FRAMEWORK", "프레임워크", "DATABASE", "DB", "INFRA", "인프라", "TOOL", "협업 도구",
            "ETC", "기타");
    private static final Map<String, String> EMPLOYMENT = Map.of(
            "FULL_TIME", "정규직", "CONTRACT", "계약직", "INTERN", "인턴", "FREELANCE", "프리랜서");
    /** 숙련도 이름과 기준 문장 (profile 의 Proficiency 와 같은 문장, 기획서 4.1) */
    private static final String[][] LEVELS = {
            {"학습 중", "공부했거나 개인 프로젝트에서 써 봤다"},
            {"실무 사용", "업무에서 써 봤고, 막히면 찾아보며 해결한다"},
            {"능숙", "설계부터 문제 해결까지 혼자 할 수 있다"},
            {"전문가", "팀의 기준을 정하고 다른 사람을 가르친다"}};
    private static final int SUMMARY_PART_MAX = 60;

    private ChunkTextBuilder() {
    }

    /** 기술명 · 카테고리 · 숙련도 기준 문장 · 사용처 */
    public static ChunkDraft skill(SkillSource s) {
        String[] level = LEVELS[Math.clamp(s.level(), 1, 4) - 1];
        List<String> lines = new ArrayList<>();
        lines.add("기술: " + s.name());
        lines.add("카테고리: " + CATEGORY.getOrDefault(s.category(), s.category()));
        lines.add("숙련도: " + level[0] + " — " + level[1]);
        if (!s.usages().isEmpty()) {
            lines.add("사용처: " + String.join(", ", s.usages()));
        }
        return new ChunkDraft(ChunkType.SKILL, s.userSkillId(), 1, null, s.name(), String.join("\n", lines),
                List.of(s.name()), null, null, null, false);
    }

    /** 회사 · 직무 · 기간 · 주요 업무 · 사용 기술. 재직 중이면 asOf 까지 개월 수를 센다 */
    public static ChunkDraft career(CareerSource c, YearMonth asOf) {
        List<String> lines = new ArrayList<>();
        String extra = joinNonBlank(", ", c.positionTitle(), EMPLOYMENT.getOrDefault(c.employmentType(), c.employmentType()));
        lines.add("경력: " + c.companyName() + " · " + c.teamName() + " · " + c.jobTitle()
                + (extra.isEmpty() ? "" : " (" + extra + ")"));
        int months = months(c.start(), c.end() == null ? asOf : c.end());
        lines.add("기간: " + c.start() + " ~ " + (c.end() == null ? "재직 중" : c.end().toString()) + " (" + months + "개월)");
        if (!c.duties().isEmpty()) {
            lines.add("주요 업무:");
            c.duties().forEach(d -> lines.add("- " + d));
        }
        if (!c.skills().isEmpty()) {
            lines.add("사용 기술: " + String.join(", ", c.skills()));
        }
        return new ChunkDraft(ChunkType.CAREER, c.careerId(), c.version(), null,
                c.companyName() + " " + c.jobTitle(), String.join("\n", lines),
                c.skills(), c.start(), c.end(), months, false);
    }

    /** 제목 · 역할 · 기술 · 확정한 사례별 한 줄 요약 */
    public static ChunkDraft projectSummary(ProjectSource p) {
        List<String> lines = new ArrayList<>();
        lines.add("프로젝트: " + p.title());
        period(p.start(), p.end()).ifPresent(text -> lines.add("기간: " + text));
        Set<String> roles = new LinkedHashSet<>();
        p.confirmedEpisodes().stream().map(EpisodeSource::role).filter(r -> r != null && !r.isBlank()).forEach(roles::add);
        if (!roles.isEmpty()) {
            lines.add("역할: " + String.join(", ", roles));
        }
        if (!p.skills().isEmpty()) {
            lines.add("사용 기술: " + String.join(", ", p.skills()));
        }
        lines.add("사례:");
        p.confirmedEpisodes().forEach(e -> lines.add("- " + e.title() + ": " + cut(firstLine(e.problem())) + " → "
                + cut(firstLine(e.solution())) + " → " + cut(firstLine(e.result()))));
        boolean hasMetric = p.confirmedEpisodes().stream().anyMatch(EpisodeSource::hasMetric);
        return new ChunkDraft(ChunkType.PROJECT_SUMMARY, p.projectId(), p.version(), p.projectId(), p.title(),
                String.join("\n", lines), p.skills(), p.start(), p.end(), monthsOrNull(p.start(), p.end()), hasMetric);
    }

    /** 프로젝트 제목 + 사례 제목 + 역할 + 문제 + 해결 + 결과 */
    public static ChunkDraft episode(ProjectSource p, EpisodeSource e) {
        List<String> lines = new ArrayList<>();
        lines.add("프로젝트: " + p.title());
        lines.add("사례: " + e.title());
        if (e.role() != null && !e.role().isBlank()) {
            lines.add("역할: " + e.role());
        }
        lines.add("문제: " + e.problem());
        lines.add("해결: " + e.solution());
        lines.add("결과: " + e.result());
        if (!e.skills().isEmpty()) {
            lines.add("사용 기술: " + String.join(", ", e.skills()));
        }
        return new ChunkDraft(ChunkType.EPISODE, e.episodeId(), e.versionNo(), p.projectId(), e.title(),
                String.join("\n", lines), e.skills(), p.start(), p.end(), monthsOrNull(p.start(), p.end()),
                e.hasMetric());
    }

    /** 시작 월부터 끝 월까지 (양 끝 포함) */
    static int months(YearMonth start, YearMonth end) {
        return (int) Math.max(0, start.until(end, ChronoUnit.MONTHS) + 1);
    }

    private static Integer monthsOrNull(YearMonth start, YearMonth end) {
        return start == null || end == null ? null : months(start, end);
    }

    private static java.util.Optional<String> period(YearMonth start, YearMonth end) {
        if (start == null && end == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Objects.toString(start, "?") + " ~ " + Objects.toString(end, "?"));
    }

    private static String joinNonBlank(String delimiter, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                kept.add(part);
            }
        }
        return String.join(delimiter, kept);
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return newline < 0 ? text : text.substring(0, newline);
    }

    private static String cut(String text) {
        return text.length() <= SUMMARY_PART_MAX ? text : text.substring(0, SUMMARY_PART_MAX - 1) + "…";
    }
}
