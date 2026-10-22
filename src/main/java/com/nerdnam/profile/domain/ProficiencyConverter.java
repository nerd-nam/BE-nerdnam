package com.nerdnam.profile.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Proficiency ↔ DB 의 level(1~4) */
@Converter
public class ProficiencyConverter implements AttributeConverter<Proficiency, Integer> {

    @Override
    public Integer convertToDatabaseColumn(Proficiency attribute) {
        return attribute == null ? null : attribute.level();
    }

    @Override
    public Proficiency convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : Proficiency.ofLevel(dbData);
    }
}
