package com.nerdnam.project.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.YearMonth;

/**
 * 프로젝트 기간 (월 단위). 임시 저장을 허용하므로 둘 다 비어 있을 수 있다.
 * 개인·팀 프로젝트 기간은 연차 계산에 넣지 않는다 (D-3).
 */
@Embeddable
public class ProjectPeriod {

    @Column(name = "period_start")
    private YearMonth startMonth;

    @Column(name = "period_end")
    private YearMonth endMonth;

    protected ProjectPeriod() {
    }

    private ProjectPeriod(YearMonth startMonth, YearMonth endMonth) {
        this.startMonth = startMonth;
        this.endMonth = endMonth;
    }

    public static ProjectPeriod of(YearMonth start, YearMonth end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "종료 월이 시작 월보다 빨라요.");
        }
        return new ProjectPeriod(start, end);
    }

    public static ProjectPeriod empty() {
        return new ProjectPeriod(null, null);
    }

    public YearMonth startMonth() {
        return startMonth;
    }

    public YearMonth endMonth() {
        return endMonth;
    }

    /** 시작~종료 개월 수 (양 끝 포함). 둘 중 하나라도 없으면 null */
    public Integer months() {
        if (startMonth == null || endMonth == null) {
            return null;
        }
        return (int) (startMonth.until(endMonth, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }
}
