package com.nerdnam.common.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class YearMonthConverterTest {

    private final YearMonthConverter converter = new YearMonthConverter();

    @Test
    void 월은_그_달_1일로_저장하고_다시_월로_읽는다() {
        LocalDate stored = converter.convertToDatabaseColumn(YearMonth.of(2023, 3));

        assertThat(stored).isEqualTo(LocalDate.of(2023, 3, 1));
        assertThat(converter.convertToEntityAttribute(stored)).isEqualTo(YearMonth.of(2023, 3));
    }

    @Test
    void null_은_null_로() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
