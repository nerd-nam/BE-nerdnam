package com.nerdnam.skill.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 도메인 단위 테스트 예시 — Spring 도, DB 도 띄우지 않는다. 그래서 1초 안에 끝난다.
 * 도메인 규칙은 이런 테스트로 먼저 고정하고, 그 다음에 저장·API 를 붙인다.
 */
class SkillNameKeyTest {

    @ParameterizedTest(name = "\"{0}\" → \"{1}\"")
    @CsvSource({
            "Spring Boot,     springboot",
            "'  React  ',     react",
            "Next.js,         next.js",
            "Apache   Kafka,  apachekafka",
            "리액트,            리액트",
    })
    @DisplayName("원문 기술명을 소문자·공백 제거 규칙으로 정규화한다")
    void normalize(String raw, String expected) {
        assertThat(SkillNameKey.of(raw).value()).isEqualTo(expected);
    }

    @Test
    @DisplayName("값이 같으면 같은 키다")
    void equality() {
        assertThat(SkillNameKey.of("Spring Boot")).isEqualTo(SkillNameKey.of("springboot"));
    }

    @Test
    @DisplayName("빈 기술명으로는 키를 만들 수 없다")
    void blank() {
        assertThatThrownBy(() -> SkillNameKey.of("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("정규화되지 않은 값으로 직접 생성할 수 없다")
    void notNormalized() {
        assertThatThrownBy(() -> new SkillNameKey("Spring Boot"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
