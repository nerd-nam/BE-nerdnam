package com.nerdnam.project.application;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.project.application.ProjectViews.Review;
import com.nerdnam.project.domain.EpisodeEdit;
import com.nerdnam.project.domain.Project;
import com.nerdnam.project.domain.ProjectRepository;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사례 결정과 AI 없이 고치기 (BE-09, S05). 누를 때마다 저장한다 (C-02 S05 7).
 * 서비스는 "불러오기 → Project 메서드 호출 → 검토 화면 응답"만 하고, 규칙은 Project 가 지킨다.
 */
@Service
@Transactional
public class EpisodeDecisionService {

    private final ProjectRepository projects;
    private final ProjectLookup lookup;

    public EpisodeDecisionService(ProjectRepository projects, ProjectLookup lookup) {
        this.projects = projects;
        this.lookup = lookup;
    }

    @Transactional(readOnly = true)
    public Review review(Long memberId, Long projectId) {
        return lookup.review(lookup.get(memberId, projectId));
    }

    public Review confirmEpisode(Long memberId, Long projectId, Long episodeId) {
        return change(memberId, projectId, p -> p.confirmEpisode(episodeId));
    }

    public Review excludeEpisode(Long memberId, Long projectId, Long episodeId) {
        return change(memberId, projectId, p -> p.excludeEpisode(episodeId));
    }

    public Review undoDecision(Long memberId, Long projectId, Long episodeId) {
        return change(memberId, projectId, p -> p.undoDecision(episodeId));
    }

    public Review dropClarification(Long memberId, Long projectId, Long episodeId, String clarificationKey) {
        return change(memberId, projectId, p -> p.dropClarification(episodeId, clarificationKey));
    }

    public Review editEpisode(Long memberId, Long projectId, Long episodeId, EpisodeEdit edit) {
        return change(memberId, projectId, p -> p.editEpisode(episodeId, edit));
    }

    public Review addEpisode(Long memberId, Long projectId, EpisodeEdit content) {
        return change(memberId, projectId, p -> p.addEpisode(content));
    }

    public Review restoreVersion(Long memberId, Long projectId, Long episodeId, int versionNo) {
        return change(memberId, projectId, p -> p.restoreVersion(episodeId, versionNo));
    }

    /**
     * 프로젝트 확정 (P1). 저장과 함께 ProjectConfirmed 가 발행되어 rag 가 같은 트랜잭션에서
     * 그 프로젝트의 청크를 새로 만든다 (임베딩은 커밋 뒤, BE-10).
     */
    public Review confirm(Long memberId, Long projectId) {
        Project project = lookup.get(memberId, projectId);
        ensureNoRunningJob(project);
        project.confirm();
        projects.saveAndFlush(project);
        return lookup.review(project);
    }

    private Review change(Long memberId, Long projectId, Consumer<Project> action) {
        Project project = lookup.get(memberId, projectId);
        ensureNoRunningJob(project);
        action.accept(project);
        projects.flush();   // 새 사례·버전의 id 를 응답에 담는다
        return lookup.review(project);
    }

    /** AI 작업이 반영되기 전에 다른 변경이 끼어들면 결과가 엇갈린다. 작업이 끝난 뒤에 하게 한다 */
    private void ensureNoRunningJob(Project project) {
        Long running = lookup.runningJobId(project);
        if (running != null) {
            throw new BusinessException(ErrorCode.AI_JOB_RUNNING, ErrorCode.AI_JOB_RUNNING.defaultMessage(),
                    java.util.Map.of("runningJobId", running));
        }
    }
}
