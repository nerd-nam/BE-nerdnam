package com.nerdnam.profile.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 재직기간 — 값 객체. 시작 월 ~ 종료 월(없으면 재직 중).
 * 연차 계산(기획서 F5, D-3·Q9)의 재료가 된다.
 */
@Embeddable
public class EmploymentPeriod {

    @Column(name = "start_month", nullable = false)
    private YearMonth startMonth;

    @Column(name = "end_month")
    private YearMonth endMonth;

    protected EmploymentPeriod() {
        // JPA
    }

    private EmploymentPeriod(YearMonth startMonth, YearMonth endMonth) {
        this.startMonth = startMonth;
        this.endMonth = endMonth;
    }

    public static EmploymentPeriod of(YearMonth startMonth, YearMonth endMonth) {
        if (startMonth == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입사 월을 입력해 주세요.");
        }
        if (endMonth != null && endMonth.isBefore(startMonth)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "퇴사 월이 입사 월보다 빨라요.");
        }
        return new EmploymentPeriod(startMonth, endMonth);
    }

    public YearMonth startMonth() {
        return startMonth;
    }

    /** 재직 중이면 null */
    public YearMonth endMonth() {
        return endMonth;
    }

    public boolean isCurrent() {
        return endMonth == null;
    }

    /** 재직 개월 수 (시작 월과 마지막 월을 모두 센다). 재직 중이면 asOf 까지 */
    public long months(YearMonth asOf) {
        YearMonth last = isCurrent() ? asOf : endMonth;
        if (last.isBefore(startMonth)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(startMonth, last) + 1;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EmploymentPeriod other
                && Objects.equals(startMonth, other.startMonth)
                && Objects.equals(endMonth, other.endMonth);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startMonth, endMonth);
    }
}
