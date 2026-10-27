package com.nerdnam.skill.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 표준 기술 사전의 기술 하나 — Skill 애그리거트 루트. 별칭(SkillAlias)을 함께 관리한다.
 * 사전은 전체 사용자 공용이다. 표준 기술(OFFICIAL)은 마이그레이션(V2, V3 …)으로만 넣는다.
 */
@Entity
@Table(name = "skill")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Skill extends BaseTimeEntity {

    private static final int MAX_NAME_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    /** 정규화 키. {@link SkillNameKey} 규칙으로 만든 값만 들어간다 */
    @Column(name = "name_key", nullable = false, length = MAX_NAME_LENGTH)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillStatus status;

    @OneToMany(mappedBy = "skill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkillAlias> aliases = new ArrayList<>();

    /** 사전에 없는 기술을 사용자가 추가할 때. 이름은 사용자가 쓴 그대로(앞뒤 공백만 제거) 표시한다 */
    public static Skill userAdded(String rawName, SkillCategory category) {
        // 카테고리를 비워 보내면 ETC(기타)로 등록한다 — 웹에 고르는 칸이 없다 (이슈 #33)
        SkillCategory chosen = category == null ? SkillCategory.ETC : category;
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기술명은 1~" + MAX_NAME_LENGTH + "자로 입력해 주세요.");
        }
        Skill skill = new Skill();
        skill.name = name;
        skill.nameKey = SkillNameKey.of(name).value();
        skill.category = chosen;
        skill.status = SkillStatus.USER_ADDED;
        return skill;
    }

    public SkillNameKey key() {
        return new SkillNameKey(nameKey);
    }

    public boolean isOfficial() {
        return status == SkillStatus.OFFICIAL;
    }

    public List<SkillAlias> getAliases() {
        return Collections.unmodifiableList(aliases);
    }
}
