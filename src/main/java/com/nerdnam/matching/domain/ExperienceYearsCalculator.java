package com.nerdnam.matching.domain;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 연차 규칙 판정 (기획서 5.4-3, 4.4).
 * <ul>
 *   <li>요건의 기술을 쓴 경력의 재직기간을 <b>합집합</b>으로 센다 — 겹치는 기간은 한 번만 (Q9)</li>
 *   <li>개인·팀 프로젝트 기간은 넣지 않는다 (D-3) — 그래서 입력은 경력뿐이다</li>
 *   <li>요건에 기술이 없으면("백엔드 개발 경력 3년 이상") 모든 경력을 센다</li>
 *   <li>판정: 요구 연차 이상 = 충족, 경력이 있지만 모자람 = 부분 충족, 없음 = 미충족</li>
 * </ul>
 */
public final class ExperienceYearsCalculator {

    private ExperienceYearsCalculator() {
    }

    /** @param end null = 재직 중 */
    public record CareerPeriod(YearMonth start, YearMonth end, Set<String> skills) {
    }

    public static int months(Collection<CareerPeriod> careers, Collection<String> requirementSkills, YearMonth asOf) {
        Set<String> wanted = requirementSkills.stream().map(ExperienceYearsCalculator::key).collect(Collectors.toSet());
        List<YearMonth[]> ranges = careers.stream()
                .filter(c -> wanted.isEmpty() || c.skills().stream().map(ExperienceYearsCalculator::key).anyMatch(wanted::contains))
                .map(c -> new YearMonth[] {c.start(), c.end() == null || c.end().isAfter(asOf) ? asOf : c.end()})
                .filter(r -> !r[1].isBefore(r[0]))
                .sorted(Comparator.comparing((YearMonth[] r) -> r[0]))
                .toList();
        int total = 0;
        YearMonth curStart = null;
        YearMonth curEnd = null;
        for (YearMonth[] r : ranges) {
            if (curStart == null) {
                curStart = r[0];
                curEnd = r[1];
            } else if (!r[0].isAfter(curEnd.plusMonths(1))) {   // 겹치거나 바로 이어짐 → 합친다
                if (r[1].isAfter(curEnd)) {
                    curEnd = r[1];
                }
            } else {
                total += span(curStart, curEnd);
                curStart = r[0];
                curEnd = r[1];
            }
        }
        if (curStart != null) {
            total += span(curStart, curEnd);
        }
        return total;
    }

    /** 연차 조건이 없으면 null (규칙 판정 없음) */
    public static Verdict judge(Integer minYears, int months) {
        if (minYears == null) {
            return null;
        }
        if (months >= minYears * 12) {
            return Verdict.MET;
        }
        return months > 0 ? Verdict.PARTIAL : Verdict.UNMET;
    }

    /** "2년 3개월" */
    public static String describe(int months) {
        int years = months / 12;
        int rest = months % 12;
        List<String> parts = new ArrayList<>();
        if (years > 0) {
            parts.add(years + "년");
        }
        if (rest > 0 || years == 0) {
            parts.add(rest + "개월");
        }
        return String.join(" ", parts);
    }

    private static int span(YearMonth start, YearMonth end) {
        return (int) (start.until(end, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    private static String key(String skill) {
        return skill.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}
