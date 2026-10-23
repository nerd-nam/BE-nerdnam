package com.nerdnam.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.error.BusinessException;
import org.junit.jupiter.api.Test;

class ProficiencyTest {

    @Test
    void 숫자_단계로_숙련도를_찾는다() {
        assertThat(Proficiency.ofLevel(1)).isEqualTo(Proficiency.LEARNING);
        assertThat(Proficiency.ofLevel(3)).isEqualTo(Proficiency.PROFICIENT);
        assertThat(Proficiency.PROFICIENT.label()).isEqualTo("능숙");
    }

    @Test
    void 범위_밖의_단계는_거부한다() {
        assertThatThrownBy(() -> Proficiency.ofLevel(5)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Proficiency.ofLevel(0)).isInstanceOf(BusinessException.class);
    }
}
