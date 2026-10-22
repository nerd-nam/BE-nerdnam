package com.nerdnam.project.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.profile.application.UserSkillView;
import com.nerdnam.project.application.ProjectViews.Detail;
import com.nerdnam.project.application.ProjectViews.Summary;
import com.nerdnam.project.domain.Project;
import com.nerdnam.project.domain.ProjectDraft;
import com.nerdnam.project.domain.ProjectRepository;
import com.nerdnam.skill.domain.SkillCategory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 프로젝트 입력 (S04): 임시 저장, 검토 결과 버리기, 재등록 시작 */
@Service
@Transactional
public class ProjectDraftService {

    private final ProjectRepository projects;
    private final ProjectLookup lookup;
    private final UserSkillService userSkills;

    public ProjectDraftService(ProjectRepository projects, ProjectLookup lookup, UserSkillService userSkills) {
        this.projects = projects;
        this.lookup = lookup;
        this.userSkills = userSkills;
    }

    @Transactional(readOnly = true)
    public List<Summary> list(Long memberId) {
        return projects.findAllByMemberIdOrderByIdDesc(memberId).stream().map(Summary::from).toList();
    }

    @Transactional(readOnly = true)
    public Detail get(Long memberId, Long projectId) {
        return lookup.detail(lookup.get(memberId, projectId));
    }

    public Detail create(Long memberId, ProjectDraft draft) {
        lookup.checkReferences(memberId, draft);
        Project saved = projects.saveAndFlush(Project.createDraft(memberId, draft));
        return lookup.detail(saved);
    }

    /** 자동 임시 저장. 화면이 받아 간 version 과 다르면 다른 곳(다른 탭)에서 먼저 고친 것 → 409 */
    public Detail update(Long memberId, Long projectId, ProjectDraft draft, Long expectedVersion) {
        Project project = lookup.get(memberId, projectId);
        if (expectedVersion == null || expectedVersion != project.getVersion()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        lookup.checkReferences(memberId, draft);
        project.updateDraft(draft);
        projects.flush();   // 올라간 version 을 응답에 담는다
        return lookup.detail(project);
    }

    /**
     * S04 "기술스택에 없는 기술 추가" — 기술스택에 "학습 중"으로 만들고(이미 있으면 그대로) 이 프로젝트에 연결한다.
     * 사전에 있는 이름이면 표준 이름으로 바뀐다 (C-02 S04 5).
     */
    public UserSkillView addSkill(Long memberId, Long projectId, String name, SkillCategory categoryIfNew) {
        Project project = lookup.get(memberId, projectId);
        UserSkillView added = userSkills.add(memberId, name, categoryIfNew, null).userSkill();
        project.addUserSkill(added.id());
        return added;
    }

    /** 확정 전 검토 결과 버리기 (기획서 v1.14) — S04 진입·온보딩 밖으로 나갈 때. 여러 번 불러도 안전 */
    public Detail discardReview(Long memberId, Long projectId) {
        Project project = lookup.get(memberId, projectId);
        Long running = lookup.runningJobId(project);
        if (running != null) {
            throw new BusinessException(ErrorCode.AI_JOB_RUNNING, ErrorCode.AI_JOB_RUNNING.defaultMessage(),
                    java.util.Map.of("runningJobId", running));
        }
        project.discardReview();
        projects.flush();
        return lookup.detail(project);
    }

    /** 재등록 시작 (S06 "수정") — 프로젝트가 초안으로 돌아가고 입력 화면부터 다시 */
    public Detail reopen(Long memberId, Long projectId) {
        Project project = lookup.get(memberId, projectId);
        project.reopen();
        projects.saveAndFlush(project);   // ProjectReopened 발행
        return lookup.detail(project);
    }
}
