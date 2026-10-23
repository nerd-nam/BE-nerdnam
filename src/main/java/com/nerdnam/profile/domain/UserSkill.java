package com.nerdnam.profile.domain;

import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 내 기술스택의 기술 하나 + 숙련도 — 작은 애그리거트.
 * 회원당 같은 기술은 하나만 (DB 유니크 uk_user_skill). 기술은 사전(skill)의 id 로만 참조한다.
 */
@Entity
@Table(name = "user_skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSkill extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    @Convert(converter = ProficiencyConverter.class)
    @Column(name = "level", nullable = false)
    private Proficiency proficiency;

    public static UserSkill add(Long memberId, Long skillId, Proficiency proficiency) {
        UserSkill userSkill = new UserSkill();
        userSkill.memberId = Objects.requireNonNull(memberId);
        userSkill.skillId = Objects.requireNonNull(skillId);
        userSkill.proficiency = Objects.requireNonNull(proficiency);
        return userSkill;
    }

    public void changeProficiency(Proficiency proficiency) {
        this.proficiency = Objects.requireNonNull(proficiency);
    }
}
