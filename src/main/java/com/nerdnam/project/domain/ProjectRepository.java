package com.nerdnam.project.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface ProjectRepository extends Repository<Project, Long> {

    /** save 로 시작하는 메서드를 부르면 Project 에 쌓인 도메인 이벤트(ProjectConfirmed 등)가 발행된다 */
    Project save(Project project);

    /** 저장하고 바로 DB 에 반영한 뒤 이벤트를 발행한다. 이벤트를 받는 쪽이 SQL 로 최신 값을 읽어야 할 때 */
    Project saveAndFlush(Project project);

    void delete(Project project);

    void flush();

    /** 소유권 확인을 겸한 조회. 남의 것이면 비어 있다 */
    Optional<Project> findByIdAndMemberId(Long id, Long memberId);

    List<Project> findAllByMemberIdOrderByIdDesc(Long memberId);
}
