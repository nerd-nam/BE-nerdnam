package com.nerdnam.skill.application;

import com.nerdnam.skill.domain.Skill;
import com.nerdnam.skill.domain.SkillCategory;

/** 다른 모듈에 건네는 기술 정보. 엔티티(Skill)를 모듈 밖으로 내보내지 않기 위한 읽기 전용 값 */
public record SkillSummary(Long id, String name, SkillCategory category, boolean official) {

    static SkillSummary from(Skill skill) {
        return new SkillSummary(skill.getId(), skill.getName(), skill.getCategory(), skill.isOfficial());
    }
}
