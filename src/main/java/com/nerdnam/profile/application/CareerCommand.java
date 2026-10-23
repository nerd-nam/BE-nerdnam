package com.nerdnam.profile.application;

import com.nerdnam.profile.domain.EmploymentType;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/** 경력 저장 요청 (컨트롤러 → 서비스). version 은 수정할 때만 쓴다 (낙관적 락) */
public record CareerCommand(
        String companyName,
        String teamName,
        String jobTitle,
        String positionTitle,
        EmploymentType employmentType,
        YearMonth startMonth,
        YearMonth endMonth,
        List<String> duties,
        Set<Long> userSkillIds,
        Long version) {
}
