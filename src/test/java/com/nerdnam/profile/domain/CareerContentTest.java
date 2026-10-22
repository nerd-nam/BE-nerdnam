package com.nerdnam.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.error.BusinessException;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CareerContentTest {

    private static final EmploymentPeriod PERIOD = EmploymentPeriod.of(YearMonth.of(2023, 3), null);

    @Test
    void 앞뒤_공백을_지우고_빈_업무_줄은_버린다() {
        CareerContent content = new CareerContent("  OO커머스 ", "결제플랫폼팀", "백엔드 개발자", "  ",
                EmploymentType.FULL_TIME, PERIOD, Arrays.asList(" 결제 API 설계 ", "", "  ", null), null);

        assertThat(content.companyName()).isEqualTo("OO커머스");
        assertThat(content.positionTitle()).isNull();
        assertThat(content.duties()).containsExactly("결제 API 설계");
        assertThat(content.userSkillIds()).isEmpty();
    }

    @Test
    void 주요_업무는_5줄까지다() {
        List<String> sixLines = List.of("1", "2", "3", "4", "5", "6");

        assertThatThrownBy(() -> new CareerContent("회사", "팀", "직무", null,
                EmploymentType.FULL_TIME, PERIOD, sixLines, Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5줄");
    }

    @Test
    void 회사명은_필수다() {
        assertThatThrownBy(() -> new CareerContent(" ", "팀", "직무", null,
                EmploymentType.FULL_TIME, PERIOD, List.of(), Set.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("회사명");
    }
}
