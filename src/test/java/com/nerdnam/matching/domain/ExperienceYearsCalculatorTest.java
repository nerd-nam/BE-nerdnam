package com.nerdnam.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.matching.domain.ExperienceYearsCalculator.CareerPeriod;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 연차 규칙 판정: 요건 기술을 쓴 경력의 재직기간 합집합 (Q9, D-3) */
class ExperienceYearsCalculatorTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    @Test
    void 겹치는_기간은_한_번만_센다_Q9() {
        List<CareerPeriod> careers = List.of(
                new CareerPeriod(YearMonth.of(2020, 1), YearMonth.of(2021, 12), Set.of("Spring Boot")),   // 24개월
                new CareerPeriod(YearMonth.of(2021, 7), YearMonth.of(2022, 6), Set.of("spring boot")));   // 6개월 겹침

        assertThat(ExperienceYearsCalculator.months(careers, List.of("Spring Boot"), NOW)).isEqualTo(30);
    }

    @Test
    void 떨어진_기간은_더한다() {
        List<CareerPeriod> careers = List.of(
                new CareerPeriod(YearMonth.of(2018, 1), YearMonth.of(2018, 12), Set.of("Java")),
                new CareerPeriod(YearMonth.of(2020, 1), YearMonth.of(2020, 6), Set.of("Java")));

        assertThat(ExperienceYearsCalculator.months(careers, List.of("Java"), NOW)).isEqualTo(18);
    }

    @Test
    void 요건_기술을_쓰지_않은_경력은_세지_않는다() {
        List<CareerPeriod> careers = List.of(
                new CareerPeriod(YearMonth.of(2018, 1), YearMonth.of(2018, 12), Set.of("Python")));

        assertThat(ExperienceYearsCalculator.months(careers, List.of("Java"), NOW)).isZero();
    }

    @Test
    void 요건에_기술이_없으면_모든_경력을_센다() {
        List<CareerPeriod> careers = List.of(
                new CareerPeriod(YearMonth.of(2024, 11), null, Set.of())); // 재직 중 → 기준 월까지 24개월

        assertThat(ExperienceYearsCalculator.months(careers, List.of(), NOW)).isEqualTo(24);
    }

    @Test
    void 요구_연차_이상이면_충족_모자라면_부분_없으면_미충족() {
        assertThat(ExperienceYearsCalculator.judge(3, 36)).isEqualTo(Verdict.MET);
        assertThat(ExperienceYearsCalculator.judge(3, 35)).isEqualTo(Verdict.PARTIAL);
        assertThat(ExperienceYearsCalculator.judge(3, 0)).isEqualTo(Verdict.UNMET);
        assertThat(ExperienceYearsCalculator.judge(null, 0)).isNull();
    }

    @Test
    void 개월_수를_사람이_읽는_말로() {
        assertThat(ExperienceYearsCalculator.describe(27)).isEqualTo("2년 3개월");
        assertThat(ExperienceYearsCalculator.describe(24)).isEqualTo("2년");
        assertThat(ExperienceYearsCalculator.describe(0)).isEqualTo("0개월");
    }

    @Test
    void 규칙과_LLM_판정이_다르면_낮은_쪽_D5() {
        assertThat(VerdictPolicy.combine(Verdict.PARTIAL, Verdict.MET)).isEqualTo(Verdict.PARTIAL);
        assertThat(VerdictPolicy.combine(Verdict.MET, Verdict.UNMET)).isEqualTo(Verdict.UNMET);
        assertThat(VerdictPolicy.combine(null, Verdict.MET)).isEqualTo(Verdict.MET);
        assertThat(Verdict.fromAi("Partial")).isEqualTo(Verdict.PARTIAL);
        assertThat(Verdict.fromAi("maybe")).as("모르는 값은 미충족").isEqualTo(Verdict.UNMET);
    }
}
