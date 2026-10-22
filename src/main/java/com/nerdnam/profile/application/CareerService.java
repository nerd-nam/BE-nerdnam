package com.nerdnam.profile.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.profile.domain.Career;
import com.nerdnam.profile.domain.CareerContent;
import com.nerdnam.profile.domain.CareerRepository;
import com.nerdnam.profile.domain.EmploymentPeriod;
import com.nerdnam.profile.domain.UserSkillRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 경력 유스케이스 (S03) */
@Service
@Transactional
public class CareerService {

    private final CareerRepository careers;
    private final UserSkillRepository userSkills;
    private final ProjectReferences projectReferences;

    public CareerService(CareerRepository careers, UserSkillRepository userSkills, ProjectReferences projectReferences) {
        this.careers = careers;
        this.userSkills = userSkills;
        this.projectReferences = projectReferences;
    }

    @Transactional(readOnly = true)
    public List<CareerView> list(Long memberId) {
        return careers.findAllByMemberIdOrderByPeriodStartMonthDescIdDesc(memberId).stream()
                .map(CareerView::from)
                .toList();
    }

    public CareerView create(Long memberId, CareerCommand command) {
        Career saved = careers.save(Career.create(memberId, toContent(memberId, command)));
        return CareerView.from(saved);
    }

    /** 화면이 받아 간 version 과 지금 version 이 다르면 다른 곳에서 먼저 고친 것 → 409 */
    public CareerView update(Long memberId, Long careerId, CareerCommand command) {
        Career career = get(memberId, careerId);
        if (command.version() == null || command.version() != career.getVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        career.update(toContent(memberId, command));
        careers.flush();   // UPDATE 를 지금 실행해 올라간 version 을 응답에 담는다
        return CareerView.from(career);
    }

    /** 프로젝트가 연결된 경력은 지우지 않고 연결된 프로젝트를 알려 준다 (ADR-0002 Q8) */
    public void delete(Long memberId, Long careerId) {
        Career career = get(memberId, careerId);
        List<Usage> usages = projectReferences.projectsOfCareer(memberId, careerId).stream()
                .map(p -> new Usage(Usage.Type.PROJECT, p.id(), p.title()))
                .toList();
        if (!usages.isEmpty()) {
            throw new BusinessException(ErrorCode.IN_USE,
                    "'" + usages.getFirst().name() + "' 프로젝트가 이 경력에 연결돼 있어요. 프로젝트 소속을 개인으로 바꿔 주세요.",
                    Map.of("usages", usages));
        }
        careers.delete(career);
    }

    private Career get(Long memberId, Long careerId) {
        return careers.findByIdAndMemberId(careerId, memberId).orElseThrow(BusinessException::notFound);
    }

    private CareerContent toContent(Long memberId, CareerCommand c) {
        Set<Long> skillIds = c.userSkillIds() == null ? Set.of() : Set.copyOf(c.userSkillIds());
        if (!skillIds.isEmpty() && userSkills.findAllByMemberIdAndIdIn(memberId, skillIds).size() != skillIds.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기술스택에 없는 기술이 들어 있어요.");
        }
        return new CareerContent(c.companyName(), c.teamName(), c.jobTitle(), c.positionTitle(),
                c.employmentType(), EmploymentPeriod.of(c.startMonth(), c.endMonth()),
                c.duties(), skillIds);
    }

}
