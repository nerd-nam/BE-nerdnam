package com.nerdnam.skill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기술 별칭 (예: react.js, 리액트 → React). Skill 애그리거트 안의 엔티티 */
@Entity
@Table(name = "skill_alias")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkillAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false, length = 100)
    private String alias;

    @Column(name = "alias_key", nullable = false, length = 100)
    private String aliasKey;
}
