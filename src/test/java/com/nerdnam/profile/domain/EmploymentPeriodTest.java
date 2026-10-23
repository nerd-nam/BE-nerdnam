package com.nerdnam.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.error.BusinessException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class EmploymentPeriodTest {

    @Test
    void 같은_달에_입사하고_퇴사하면_1개월이다() {
        EmploymentPeriod period = EmploymentPeriod.of(YearMonth.of(2023, 3), YearMonth.of(2023, 3));

        assertThat(period.months(YearMonth.of(2026, 10))).isEqualTo(1);
    }

    @Test
    void 재직_중이면_기준_월까지_센다() {
        EmploymentPeriod period = EmploymentPeriod.of(YearMonth.of(2023, 3), null);

        assertThat(period.isCurrent()).isTrue();
        assertThat(period.months(YearMonth.of(2024, 2))).isEqualTo(12);
    }

    @Test
    void 퇴사_월이_입사_월보다_빠르면_거부한다() {
        assertThatThrownBy(() -> EmploymentPeriod.of(YearMonth.of(2023, 3), YearMonth.of(2023, 2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("퇴사 월");
    }

    @Test
    void 입사_월은_필수다() {
        assertThatThrownBy(() -> EmploymentPeriod.of(null, null)).isInstanceOf(BusinessException.class);
    }
}
