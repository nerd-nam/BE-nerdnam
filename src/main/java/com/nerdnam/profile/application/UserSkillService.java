package com.nerdnam.profile.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.profile.domain.CareerRepository;
import com.nerdnam.profile.domain.Proficiency;
import com.nerdnam.profile.domain.UserSkill;
import com.nerdnam.profile.domain.UserSkillRepository;
import com.nerdnam.skill.application.SkillCatalog;
import com.nerdnam.skill.application.SkillSummary;
import com.nerdnam.skill.domain.SkillCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기술스택 유스케이스 (S02, S04 "기술스택에 없는 기술 추가") */
@Service
@Transactional
public class UserSkillService {

    private final UserSkillRepository userSkills;
    private final CareerRepository careers;
    private final SkillCatalog skillCatalog;
    private final ProjectReferences projectReferences;

    public UserSkillService(UserSkillRepository userSkills, CareerRepository careers,
                            SkillCatalog skillCatalog, ProjectReferences projectReferences) {
        this.userSkills = userSkills;
        this.careers = careers;
        this.skillCatalog = skillCatalog;
        this.projectReferences = projectReferences;
    }

    @Transactional(readOnly = true)
    public List<UserSkillView> list(Long memberId) {
        List<UserSkill> mine = userSkills.findAllByMemberIdOrderByIdAsc(memberId);
        Map<Long, SkillSummary> skills = skillCatalog.getAll(mine.stream().map(UserSkill::getSkillId).toList())
                .stream().collect(Collectors.toMap(SkillSummary::id, Function.identity()));
        return mine.stream().map(us -> toView(us, skills.get(us.getSkillId()))).toList();
    }

    /**
     * 기술 추가. 이름을 사전으로 정규화하고(리액트 → React), 사전에 없으면 사용자 추가 기술로 등록한다.
     * 이미 기술스택에 있는 기술이면 새로 만들지 않고 그대로 돌려준다 (created = false).
     */
    public AddResult add(Long memberId, String name, SkillCategory categoryIfNew, Integer level) {
        SkillSummary skill = skillCatalog.findOrRegister(name, categoryIfNew);
        return userSkills.findByMemberIdAndSkillId(memberId, skill.id())
                .map(existing -> new AddResult(toView(existing, skill), false))
                .orElseGet(() -> {
                    Proficiency proficiency = level == null ? Proficiency.LEARNING : Proficiency.ofLevel(level);
                    UserSkill saved = userSkills.save(UserSkill.add(memberId, skill.id(), proficiency));
                    return new AddResult(toView(saved, skill), true);
                });
    }

    public UserSkillView changeLevel(Long memberId, Long userSkillId, int level) {
        UserSkill userSkill = get(memberId, userSkillId);
        userSkill.changeProficiency(Proficiency.ofLevel(level));   // 변경 감지로 UPDATE 된다 (save 불필요)
        SkillSummary skill = skillCatalog.getAll(List.of(userSkill.getSkillId())).getFirst();
        return toView(userSkill, skill);
    }

    /** 경력·프로젝트에서 쓰는 기술이면 지우지 않고 사용처를 알려 준다 (ADR-0002 Q7) */
    public void delete(Long memberId, Long userSkillId) {
        UserSkill userSkill = get(memberId, userSkillId);
        List<Usage> usages = new ArrayList<>();
        careers.findAllUsingSkill(memberId, userSkillId)
                .forEach(c -> usages.add(new Usage(Usage.Type.CAREER, c.getId(), c.getCompanyName())));
        projectReferences.projectsUsingUserSkill(memberId, userSkillId)
                .forEach(p -> usages.add(new Usage(Usage.Type.PROJECT, p.id(), p.title())));
        if (!usages.isEmpty()) {
            Usage first = usages.getFirst();
            String where = first.type() == Usage.Type.CAREER ? first.name() + " 경력" : "'" + first.name() + "' 프로젝트";
            String more = usages.size() > 1 ? " 외 " + (usages.size() - 1) + "곳" : "";
            throw new BusinessException(ErrorCode.IN_USE,
                    where + more + "에서 사용 중이라 삭제할 수 없어요.", Map.of("usages", usages));
        }
        userSkills.delete(userSkill);
    }

    private UserSkill get(Long memberId, Long userSkillId) {
        return userSkills.findByIdAndMemberId(userSkillId, memberId).orElseThrow(BusinessException::notFound);
    }

    private static UserSkillView toView(UserSkill us, SkillSummary skill) {
        return new UserSkillView(us.getId(), us.getSkillId(), skill.name(), skill.category(), us.getProficiency());
    }

    public record AddResult(UserSkillView userSkill, boolean created) {
    }
}
