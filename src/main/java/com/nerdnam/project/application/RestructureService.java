package com.nerdnam.project.application;

import com.nerdnam.aijob.application.AiJobs;
import com.nerdnam.aijob.application.JobRequest;
import com.nerdnam.aijob.application.JobWork;
import com.nerdnam.aijob.domain.JobType;
import com.nerdnam.aijob.domain.TargetType;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.RestructureRequest;
import com.nerdnam.infra.ai.RestructureResult;
import com.nerdnam.project.domain.AiTrace;
import com.nerdnam.project.domain.Clarification;
import com.nerdnam.project.domain.EpisodeContent;
import com.nerdnam.project.domain.Project;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 재구성 (BE-08) — 나누기·다시 나누기·수정 요청·확인 답변. 모두 AI 작업이고 횟수 제한이 없다 (ADR-0002).
 * 같은 프로젝트의 AI 작업은 동시에 1건만 (두 번째는 409 + 진행 중인 작업 id).
 *
 * <p>트랜잭션은 {@link AiJobs}가 나눈다: ① 검사·입력 만들기 → AI 호출(트랜잭션 밖) → ② 반영.
 * 그래서 이 클래스에는 {@code @Transactional}이 없다.
 */
@Service
public class RestructureService {

    public static final String SPLIT = "SPLIT";
    public static final String RESPLIT = "RESPLIT";
    public static final String REVISION = "REVISION";
    public static final String ANSWER = "ANSWER";

    private final ProjectLookup lookup;
    private final AiJobs aiJobs;
    private final AiServerClient ai;

    public RestructureService(ProjectLookup lookup, AiJobs aiJobs, AiServerClient ai) {
        this.lookup = lookup;
        this.aiJobs = aiJobs;
        this.ai = ai;
    }

    /** 나누기 (S04 "재구성하기") — 지금 원문으로 새로 나눈다. 재등록 중이면 이전 확정 사례를 맥락으로 보낸다 */
    public Long split(Long memberId, Long projectId) {
        return aiJobs.start(job(memberId, projectId, SPLIT), JobWork.of(
                () -> {
                    Project p = lookup.get(memberId, projectId);
                    p.checkCanSplit();
                    return splitRequest(p, List.of(), null);
                },
                ai::restructure,
                result -> applySplit(memberId, projectId, result, null)));
    }

    /** 다시 나누기 (S05) — 피드백 필수. 이번 검토 사례의 결정은 모두 초기화된다 (D-2) */
    public Long resplit(Long memberId, Long projectId, String feedback) {
        return aiJobs.start(job(memberId, projectId, RESPLIT), JobWork.of(
                () -> {
                    Project p = lookup.get(memberId, projectId);
                    p.checkCanResplit(feedback);
                    List<EpisodeContent> current = p.reviewEpisodes().stream().map(e -> e.current().content()).toList();
                    return splitRequest(p, current, feedback);
                },
                ai::restructure,
                result -> applySplit(memberId, projectId, result, feedback)));
    }

    /** 사례 수정 요청 (S05) */
    public Long revise(Long memberId, Long projectId, Long episodeId, String feedback) {
        return aiJobs.start(job(memberId, projectId, REVISION), JobWork.of(
                () -> {
                    Project p = lookup.get(memberId, projectId);
                    p.checkCanRevise(episodeId, feedback);
                    return episodeRequest(p, p.episode(episodeId).current().content(), p.sourceText(), feedback, null);
                },
                ai::restructure,
                result -> {
                    Project p = lookup.get(memberId, projectId);
                    p.applyRevision(episodeId, single(result, p.sourceText().length()), lookup.skillNames(p),
                            trace(result), feedback);
                    return projectId;
                }));
    }

    /** 확인 질문에 답하기 (S05) — 답변을 원문 뒤에 붙여 그 사례의 다음 버전을 만든다 */
    public Long answer(Long memberId, Long projectId, Long episodeId, String clarificationKey, String answer) {
        return aiJobs.start(job(memberId, projectId, ANSWER), JobWork.of(
                () -> {
                    Project p = lookup.get(memberId, projectId);
                    Clarification q = p.checkCanAnswer(episodeId, clarificationKey, answer);
                    String source = p.sourceTextWith(q.question(), answer);
                    return episodeRequest(p, p.episode(episodeId).current().content(), source, null,
                            new RestructureRequest.Answer(q.question(), answer.strip()));
                },
                ai::restructure,
                result -> {
                    Project p = lookup.get(memberId, projectId);
                    Clarification q = p.checkCanAnswer(episodeId, clarificationKey, answer);
                    String sourceAfter = p.sourceTextWith(q.question(), answer);   // 답변을 붙인 뒤의 원문 (구간 검사용)
                    p.applyAnswer(episodeId, clarificationKey, answer, single(result, sourceAfter.length()),
                            lookup.skillNames(p), trace(result));
                    return projectId;
                }));
    }

    // ------------------------------------------------------------------ 내부

    private Long applySplit(Long memberId, Long projectId, RestructureResult result, String feedback) {
        Project p = lookup.get(memberId, projectId);
        int length = p.sourceText().length();
        List<EpisodeContent> drafts = result.episodes() == null ? List.of()
                : result.episodes().stream().map(e -> AiEpisodeMapper.toDomain(e, length)).toList();
        p.applySplit(drafts, lookup.skillNames(p), trace(result), feedback);
        return projectId;
    }

    private RestructureRequest splitRequest(Project p, List<EpisodeContent> current, String feedback) {
        return new RestructureRequest(RestructureRequest.MODE_SPLIT, p.getTitle(), p.sourceText(), lookup.skillNames(p),
                p.previouslyConfirmedContents().stream().map(AiEpisodeMapper::toAi).toList(),
                current.stream().map(AiEpisodeMapper::toAi).toList(), null, feedback, null);
    }

    private RestructureRequest episodeRequest(Project p, EpisodeContent target, String sourceText, String feedback,
                                              RestructureRequest.Answer answer) {
        return new RestructureRequest(RestructureRequest.MODE_EPISODE, p.getTitle(), sourceText, lookup.skillNames(p),
                p.previouslyConfirmedContents().stream().map(AiEpisodeMapper::toAi).toList(), List.of(),
                AiEpisodeMapper.toAi(target), feedback, answer);
    }

    private static EpisodeContent single(RestructureResult result, int sourceLength) {
        if (result.episodes() == null || result.episodes().isEmpty()) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "AI 가 고친 사례를 돌려주지 않았어요.");
        }
        return AiEpisodeMapper.toDomain(result.episodes().getFirst(), sourceLength);
    }

    private static AiTrace trace(RestructureResult result) {
        return new AiTrace(result.model(), result.promptVersion());
    }

    private static JobRequest job(Long memberId, Long projectId, String mode) {
        return JobRequest.of(memberId, JobType.RESTRUCTURE, mode, TargetType.PROJECT, projectId);
    }
}
