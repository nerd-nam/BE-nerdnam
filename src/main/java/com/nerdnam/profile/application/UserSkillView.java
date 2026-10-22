package com.nerdnam.profile.application;

import com.nerdnam.profile.domain.Proficiency;
import com.nerdnam.skill.domain.SkillCategory;

/** 기술스택 항목 + 사전의 기술 이름·카테고리를 합친 조회 결과 */
public record UserSkillView(Long id, Long skillId, String name, SkillCategory category, Proficiency proficiency) {
}
