package com.nerdnam.profile.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CareerRepository extends Repository<Career, Long> {

    Career save(Career career);

    void delete(Career career);

    /** 지금까지의 변경을 DB 에 바로 반영한다 (올라간 version 을 읽어야 할 때) */
    void flush();

    /** 최신순 (입사 월이 늦은 것부터) */
    List<Career> findAllByMemberIdOrderByPeriodStartMonthDescIdDesc(Long memberId);

    Optional<Career> findByIdAndMemberId(Long id, Long memberId);

    @Query("select c from Career c join c.userSkillIds s where c.memberId = :memberId and s = :userSkillId")
    List<Career> findAllUsingSkill(@Param("memberId") Long memberId, @Param("userSkillId") Long userSkillId);
}
