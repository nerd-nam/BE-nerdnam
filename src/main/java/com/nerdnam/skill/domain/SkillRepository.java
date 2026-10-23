package com.nerdnam.skill.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Skill 애그리거트 저장소. 필요한 메서드만 연다 (JpaRepository 를 상속하지 않는 이유: deleteAll 같은
 * 위험한 메서드가 저절로 생기지 않게).
 */
public interface SkillRepository extends Repository<Skill, Long> {

    Skill save(Skill skill);

    Optional<Skill> findByNameKey(String nameKey);

    List<Skill> findAllByIdIn(Collection<Long> ids);

    @Query("select a.skill from SkillAlias a where a.aliasKey = :aliasKey")
    Optional<Skill> findByAliasKey(@Param("aliasKey") String aliasKey);

    /** 표준 기술 중 이름 키나 별칭 키에 pattern(LIKE)이 들어간 것 */
    @Query("""
            select distinct s from Skill s left join s.aliases a
            where s.status = com.nerdnam.skill.domain.SkillStatus.OFFICIAL
              and (s.nameKey like :pattern escape '\\' or a.aliasKey like :pattern escape '\\')
            """)
    List<Skill> searchOfficial(@Param("pattern") String pattern);
}
