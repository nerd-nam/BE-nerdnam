package com.nerdnam.profile.application;

import com.nerdnam.profile.domain.Career;
import com.nerdnam.profile.domain.EmploymentType;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

/**
 * 경력 조회 결과. 트랜잭션 안에서 엔티티를 이 값으로 바꿔 돌려준다
 * (open-in-view 가 꺼져 있어 트랜잭션 밖에서는 지연 로딩 컬렉션을 읽을 수 없다).
 */
public record CareerView(
        Long id,
        String companyName,
        String teamName,
        String jobTitle,
        String positionTitle,
        EmploymentType employmentType,
        YearMonth startMonth,
        YearMonth endMonth,
        List<String> duties,
        Set<Long> userSkillIds,
        long version) {

    static CareerView from(Career career) {
        return new CareerView(career.getId(), career.getCompanyName(), career.getTeamName(), career.getJobTitle(),
                career.getPositionTitle(), career.getEmploymentType(),
                career.getPeriod().startMonth(), career.getPeriod().endMonth(),
                List.copyOf(career.getDuties()), Set.copyOf(career.getUserSkillIds()), career.getVersion());
    }
}
