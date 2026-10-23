package com.nerdnam.matching.domain;

import java.util.List;

/**
 * 분석 시점의 요건 복사본 (문구·유형·연차·기술). 사용자가 요건을 고치고 다시 분석해도 이전 결과가 깨지지 않는다.
 *
 * @param type REQUIRED / PREFERRED
 */
public record RequirementSnapshot(Long requirementId, String type, String text, int displayOrder, List<String> skills,
                                  Integer minYears) {

    public RequirementSnapshot {
        skills = skills == null ? List.of() : List.copyOf(skills);
    }

    public boolean isRequired() {
        return "REQUIRED".equals(type);
    }
}
