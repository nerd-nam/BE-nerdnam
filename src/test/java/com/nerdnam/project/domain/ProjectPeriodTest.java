package com.nerdnam.project.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.nerdnam.common.error.BusinessException;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class ProjectPeriodTest {

    @Test
    void 이번_달은_허용하지만_미래의_시작과_종료_월은_거부한다() {
        YearMonth current = YearMonth.now(java.time.ZoneId.of("Asia/Seoul"));
        assertThat(ProjectPeriod.of(current, current).endMonth()).isEqualTo(current);
        assertThat(ProjectPeriod.of(current, null).endMonth()).isNull();
        assertThatThrownBy(() -> ProjectPeriod.of(current.plusMonths(1), null))
                .isInstanceOf(BusinessException.class).hasMessage("이번 달까지만 입력할 수 있어요.");
        assertThatThrownBy(() -> ProjectPeriod.of(current, current.plusMonths(1)))
                .isInstanceOf(BusinessException.class).hasMessage("이번 달까지만 입력할 수 있어요.");
    }
}
