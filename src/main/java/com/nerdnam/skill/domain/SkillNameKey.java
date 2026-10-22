package com.nerdnam.skill.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * 기술명 정규화 키 — 값 객체(Value Object)의 첫 예시.
 *
 * <ul>
 *   <li>값이 같으면 같은 것으로 본다 (record 가 equals/hashCode 를 값으로 만들어 준다).</li>
 *   <li>만들어지는 순간 규칙을 검사하므로, SkillNameKey 를 받은 코드는 "올바른 키"라고 믿고 쓸 수 있다.</li>
 *   <li>규칙: 앞뒤 공백 제거 → 소문자 → 모든 공백 제거. ("Spring Boot" → "springboot")</li>
 * </ul>
 *
 * DB 의 app.skill.name_key, app.skill_alias.alias_key 는 이 규칙으로 만든 값이다. (V2 시드 참고)
 */
public record SkillNameKey(String value) {

    private static final int MAX_LENGTH = 100;

    public SkillNameKey {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("기술명이 비어 있어요.");
        }
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("기술명은 " + MAX_LENGTH + "자 이하여야 해요.");
        }
        if (!value.equals(normalize(value))) {
            throw new IllegalArgumentException("정규화되지 않은 키예요: " + value);
        }
    }

    /** 사용자가 입력한 원문 기술명으로 키를 만든다. */
    public static SkillNameKey of(String rawName) {
        Objects.requireNonNull(rawName, "rawName");
        return new SkillNameKey(normalize(rawName));
    }

    private static String normalize(String raw) {
        return raw.strip()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }
}
