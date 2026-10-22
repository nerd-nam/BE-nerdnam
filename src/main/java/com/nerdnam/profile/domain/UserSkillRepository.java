package com.nerdnam.profile.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface UserSkillRepository extends Repository<UserSkill, Long> {

    UserSkill save(UserSkill userSkill);

    void delete(UserSkill userSkill);

    void flush();

    List<UserSkill> findAllByMemberIdOrderByIdAsc(Long memberId);

    /** 소유권 확인을 겸한 조회. 남의 것이면 비어 있다 */
    Optional<UserSkill> findByIdAndMemberId(Long id, Long memberId);

    Optional<UserSkill> findByMemberIdAndSkillId(Long memberId, Long skillId);

    List<UserSkill> findAllByMemberIdAndIdIn(Long memberId, Collection<Long> ids);
}
