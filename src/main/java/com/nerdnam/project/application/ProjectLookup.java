package com.nerdnam.project.application;

import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.profile.application.CareerService;
import com.nerdnam.profile.application.CareerView;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.project.application.ProjectViews.Detail;
import com.nerdnam.project.application.ProjectViews.EpisodeView;
import com.nerdnam.project.application.ProjectViews.Review;
import com.nerdnam.project.application.ProjectViews.SkillRef;
import com.nerdnam.project.application.ProjectViews.SupplementView;
import com.nerdnam.project.domain.Project;
import com.nerdnam.project.domain.ProjectDraft;
import com.nerdnam.project.domain.ProjectRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 프로젝트 서비스들이 함께 쓰는 조회·검사 도우미: 소유권 확인 조회, 다른 모듈(기술스택·경력) 확인,
 * 조회 결과 만들기. 모두 부르는 쪽의 트랜잭션 안에서 쓴다.
 */
@Component
class ProjectLookup {

    private final ProjectRepository projects;
    private final UserSkillService userSkills;
    private final CareerService careers;
    private final AiJobs aiJobs;

    ProjectLookup(ProjectRepository projects, UserSkillService userSkills, CareerService careers, AiJobs aiJobs) {
        this.projects = projects;
        this.userSkills = userSkills;
        this.careers = careers;
        this.aiJobs = aiJobs;
    }

    Project get(Long memberId, Long projectId) {
        return projects.findByIdAndMemberId(projectId, memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "프로젝트를 찾을 수 없어요."));
    }

    /** 고른 기술·경력이 내 것인지 확인한다 (다른 회원의 id 를 넣지 못하게) */
    void checkReferences(Long memberId, ProjectDraft draft) {
        if (!draft.userSkillIds().isEmpty()
                && userSkills.getAll(memberId, draft.userSkillIds()).size() != draft.userSkillIds().size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "기술스택에 없는 기술이 들어 있어요.");
        }
        if (draft.careerId() != null && careers.find(memberId, draft.careerId()).isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연결할 경력을 찾을 수 없어요.");
        }
    }

    List<SkillRef> skills(Project project) {
        return userSkills.getAll(project.getMemberId(), project.getUserSkillIds()).stream()
                .map(s -> new SkillRef(s.id(), s.name()))
                .toList();
    }

    List<String> skillNames(Project project) {
        return skills(project).stream().map(SkillRef::name).toList();
    }

    Long runningJobId(Project project) {
        return aiJobs.runningJobId(TargetType.PROJECT, project.getId(), "").orElse(null);
    }

    Detail detail(Project p) {
        return new Detail(p.getId(), p.getTitle(), p.getStatus(), p.isReregistering(), p.getAffiliation(),
                p.getCareerId(), companyName(p), p.getPeriod().startMonth(), p.getPeriod().endMonth(), p.getRawText(),
                skills(p), List.copyOf(p.getLinks()), supplements(p), p.getSplitRound(), p.getGenerationCount(),
                p.getVersion(), runningJobId(p));
    }

    Review review(Project p) {
        return new Review(p.getId(), p.getTitle(), p.getStatus(), p.isReregistering(), p.getAffiliation(),
                companyName(p), p.getPeriod().startMonth(), p.getPeriod().endMonth(), p.getRawText(), p.sourceText(),
                supplements(p), skills(p), p.getSplitRound(), p.getGenerationCount(),
                p.visibleEpisodes().stream().map(EpisodeView::from).toList(), runningJobId(p));
    }

    String companyName(Project p) {
        return careers.find(p.getMemberId(), p.getCareerId()).map(CareerView::companyName).orElse(null);
    }

    private static List<SupplementView> supplements(Project p) {
        return p.visibleSupplements().stream().map(SupplementView::from).toList();
    }
}
