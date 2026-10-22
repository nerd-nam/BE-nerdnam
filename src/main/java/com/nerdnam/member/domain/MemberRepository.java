package com.nerdnam.member.domain;

import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MemberRepository extends Repository<Member, Long> {

    Member save(Member member);

    void delete(Member member);

    Optional<Member> findById(Long id);

    /** 이메일은 대소문자를 무시하고 유니크 (uk_member_email) */
    @org.springframework.data.jpa.repository.Query("select count(m) > 0 from Member m where lower(m.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@org.springframework.data.repository.query.Param("email") String email);
}
