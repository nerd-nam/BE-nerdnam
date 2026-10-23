package com.nerdnam.common.jpa;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 월 단위 기간(YYYY-MM)을 DB 의 date(그 달 1일)로 저장한다.
 * DB 에는 1일만 들어가도록 CHECK 가 걸려 있다 (예: app.career.start_month).
 * autoApply = true 라서 YearMonth 필드에는 따로 표시하지 않아도 이 변환기가 쓰인다.
 */
@Converter(autoApply = true)
public class YearMonthConverter implements AttributeConverter<YearMonth, LocalDate> {

    @Override
    public LocalDate convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : attribute.atDay(1);
    }

    @Override
    public YearMonth convertToEntityAttribute(LocalDate dbData) {
        return dbData == null ? null : YearMonth.from(dbData);
    }
}
