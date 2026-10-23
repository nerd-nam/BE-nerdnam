package com.nerdnam.profile.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 경력 양식 한 벌 (기획서 4.2) — 값 객체. 만들 때 규칙을 검사하고 앞뒤 공백·빈 줄을 정리한다.
 * Career 를 만들거나 고칠 때 이 객체 하나로 받는다.
 */
public record CareerContent(
        String companyName,
        String teamName,
        String jobTitle,
        String positionTitle,
        EmploymentType employmentType,
        EmploymentPeriod period,
        List<String> duties,
        Set<Long> userSkillIds) {

    public static final int MAX_DUTIES = 5;
    private static final int MAX_DUTY_LENGTH = 300;

    public CareerContent {
        companyName = required(companyName, "회사명", 100);
        teamName = required(teamName, "부서", 100);
        jobTitle = required(jobTitle, "직무", 100);
        positionTitle = optional(positionTitle, "직급", 50);
        if (employmentType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "고용형태를 골라 주세요.");
        }
        Objects.requireNonNull(period, "period");
        duties = (duties == null ? List.<String>of() : duties).stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(line -> !line.isEmpty())        // 빈 줄은 저장하지 않는다
                .toList();
        if (duties.size() > MAX_DUTIES) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주요 업무는 " + MAX_DUTIES + "줄까지 쓸 수 있어요.");
        }
        if (duties.stream().anyMatch(line -> line.length() > MAX_DUTY_LENGTH)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "주요 업무 한 줄은 " + MAX_DUTY_LENGTH + "자 이하로 써 주세요.");
        }
        userSkillIds = userSkillIds == null ? Set.of() : Set.copyOf(userSkillIds);
    }

    private static String required(String value, String label, int max) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "을(를) 입력해 주세요.");
        }
        if (stripped.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, label + "은(는) " + max + "자 이하로 입력해 주세요.");
        }
        return stripped;
    }

    private static String optional(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, label, max);
    }
}
