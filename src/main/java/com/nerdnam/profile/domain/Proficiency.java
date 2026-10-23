package com.nerdnam.profile.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.Arrays;

/**
 * 숙련도 4단계 (기획서 4.1). 기준 문장을 enum 이 함께 가져서 화면 설명과 청크 텍스트(4.4)가 같은 문장을 쓴다.
 * DB 에는 숫자(level 1~4)로 저장한다 → {@link ProficiencyConverter}.
 */
public enum Proficiency {

    LEARNING(1, "학습 중", "공부했거나 개인 프로젝트에서 써 봤다"),
    WORKING(2, "실무 사용", "업무에서 써 봤고, 막히면 찾아보며 해결한다"),
    PROFICIENT(3, "능숙", "설계부터 문제 해결까지 혼자 할 수 있다"),
    EXPERT(4, "전문가", "팀의 기준을 정하고 다른 사람을 가르친다");

    private final int level;
    private final String label;
    private final String criteria;

    Proficiency(int level, String label, String criteria) {
        this.level = level;
        this.label = label;
        this.criteria = criteria;
    }

    public static Proficiency ofLevel(int level) {
        return Arrays.stream(values())
                .filter(p -> p.level == level)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "숙련도는 1~4 중에서 골라 주세요."));
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public String criteria() {
        return criteria;
    }
}
