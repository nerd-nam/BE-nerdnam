package com.nerdnam.skill.application;

import com.nerdnam.skill.domain.Skill;
import com.nerdnam.skill.domain.SkillCategory;
import com.nerdnam.skill.domain.SkillNameKey;
import com.nerdnam.skill.domain.SkillRepository;
import com.nerdnam.skill.domain.UnregisteredSkillNameLog;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 표준 기술 사전 — skill 모듈의 공개 입구. profile(기술스택), jd(요건 정규화)가 이 서비스만 쓴다.
 */
@Service
@Transactional(readOnly = true)
public class SkillCatalog {

    private static final int DEFAULT_LIMIT = 10;

    private final SkillRepository skills;
    private final UnregisteredSkillNameLog unregisteredLog;

    public SkillCatalog(SkillRepository skills, UnregisteredSkillNameLog unregisteredLog) {
        this.skills = skills;
        this.unregisteredLog = unregisteredLog;
    }

    /** 원문 기술명 → 사전의 기술 (이름 키 또는 별칭 키가 같은 것). 예: "리액트" → React */
    public Optional<SkillSummary> find(String rawName) {
        return findEntity(SkillNameKey.of(rawName)).map(SkillSummary::from);
    }

    /**
     * 기술스택에 추가할 기술을 찾고, 사전에 없으면 사용자 추가 기술(USER_ADDED)로 등록한다.
     * 등록할 때는 카테고리가 필요하다. 사전에 없던 이름은 사전 보강용으로 기록한다.
     */
    @Transactional
    public SkillSummary findOrRegister(String rawName, SkillCategory categoryIfNew) {
        SkillNameKey key = SkillNameKey.of(rawName);
        return findEntity(key)
                .map(SkillSummary::from)
                .orElseGet(() -> {
                    Skill added = skills.save(Skill.userAdded(rawName, categoryIfNew));
                    unregisteredLog.record(rawName, key, UnregisteredSkillNameLog.Source.PROFILE);
                    return SkillSummary.from(added);
                });
    }

    /**
     * 공고 요건에서 뽑은 기술명 원문을 표준 이름으로 바꾼다 (F4: 정규화는 API 가 한다).
     * 사전에 없는 이름은 원문 그대로 두고 사전 보강용으로 기록한다. 같은 기술은 한 번만.
     */
    @Transactional
    public List<String> normalizeForPosting(Collection<String> rawNames) {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        for (String raw : rawNames) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            SkillNameKey key = SkillNameKey.of(raw);
            Optional<Skill> found = findEntity(key);
            if (found.isPresent()) {
                names.add(found.get().getName());
            } else {
                unregisteredLog.record(raw.strip(), key, UnregisteredSkillNameLog.Source.JOB_POSTING);
                names.add(raw.strip());
            }
        }
        return List.copyOf(names);
    }

    /**
     * S02 자동완성. 표준 기술만, 이름·별칭에 검색어가 들어간 것.
     * 순서: 키가 똑같은 것 → 앞부분이 같은 것 → 들어 있는 것, 같은 순위 안에서는 이름순.
     */
    public List<SkillSummary> autocomplete(String query, SkillCategory category) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String key = SkillNameKey.of(query).value();
        return skills.searchOfficial("%" + escapeLike(key) + "%").stream()
                .filter(skill -> category == null || skill.getCategory() == category)
                .sorted(Comparator.comparingInt((Skill skill) -> rank(skill, key))
                        .thenComparing(Skill::getName, String.CASE_INSENSITIVE_ORDER))
                .limit(DEFAULT_LIMIT)
                .map(SkillSummary::from)
                .toList();
    }

    public List<SkillSummary> getAll(Collection<Long> skillIds) {
        if (skillIds.isEmpty()) {
            return List.of();
        }
        return skills.findAllByIdIn(skillIds).stream().map(SkillSummary::from).toList();
    }

    private Optional<Skill> findEntity(SkillNameKey key) {
        return skills.findByNameKey(key.value()).or(() -> skills.findByAliasKey(key.value()));
    }

    private static int rank(Skill skill, String key) {
        if (skill.getNameKey().equals(key) || skill.getAliases().stream().anyMatch(a -> a.getAliasKey().equals(key))) {
            return 0;
        }
        if (skill.getNameKey().startsWith(key) || skill.getAliases().stream().anyMatch(a -> a.getAliasKey().startsWith(key))) {
            return 1;
        }
        return 2;
    }

    /** LIKE 의 특수문자(%, _)와 escape 문자(\)를 글자 그대로 찾도록 바꾼다 */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
