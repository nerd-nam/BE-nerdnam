package com.nerdnam.profile.application;

import java.util.List;

/**
 * profile 이 "이 기술·경력을 쓰는 프로젝트가 있나"를 묻는 창구 (포트).
 *
 * <p>프로젝트는 project 모듈의 것이고, project 는 이미 profile(기술·경력 id)을 참조한다.
 * profile 이 project 를 직접 부르면 서로 참조하는 순환이 생기므로, profile 이 필요한 질문만
 * 인터페이스로 정하고 구현은 project 모듈이 한다 (의존성 역전). 구현: project.infrastructure.ProjectReferencesJdbcAdapter
 */
public interface ProjectReferences {

    List<ProjectRef> projectsUsingUserSkill(Long memberId, Long userSkillId);

    List<ProjectRef> projectsOfCareer(Long memberId, Long careerId);

    record ProjectRef(Long id, String title) {
    }
}
