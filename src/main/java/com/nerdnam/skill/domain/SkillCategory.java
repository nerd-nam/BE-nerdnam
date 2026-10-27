package com.nerdnam.skill.domain;

/**
 * 기술 카테고리 (기획서 4.1).
 *
 * <p>S02 의 탭과 같았는데, 웹에서 카테고리를 고르는 칸을 없애(사용자 결정 2026-10-27, 이슈 #33)
 * 이제 <b>사용자가 직접 고르지 않는다</b>. 사전에 없는 기술은 카테고리 없이 들어와 {@link #ETC} 로 등록된다.
 * 표준 기술(OFFICIAL)의 카테고리는 사전이 정한 값을 그대로 쓴다.
 */
public enum SkillCategory {
    LANGUAGE,   // 언어
    FRAMEWORK,  // 프레임워크
    DATABASE,   // DB
    INFRA,      // 인프라
    TOOL,       // 협업 도구
    ETC         // 기타 — 사용자가 추가한 기술의 기본값 (사전 보강 때 사람이 분류한다)
}
