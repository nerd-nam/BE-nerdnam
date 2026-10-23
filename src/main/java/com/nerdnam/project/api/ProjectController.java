package com.nerdnam.project.api;

import com.nerdnam.aijob.api.JobAccepted;
import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.profile.application.UserSkillView;
import com.nerdnam.project.application.EpisodeDecisionService;
import com.nerdnam.project.application.ProjectDraftService;
import com.nerdnam.project.application.ProjectViews.Detail;
import com.nerdnam.project.application.ProjectViews.Review;
import com.nerdnam.project.application.ProjectViews.Summary;
import com.nerdnam.project.application.RestructureService;
import com.nerdnam.project.domain.Affiliation;
import com.nerdnam.project.domain.EpisodeEdit;
import com.nerdnam.project.domain.Metric;
import com.nerdnam.project.domain.ProjectDraft;
import com.nerdnam.project.domain.ProjectPeriod;
import com.nerdnam.skill.domain.SkillCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프로젝트 입력(S04)·재구성 검토(S05)·재등록(S06 "수정").
 * AI 를 부르는 요청(나누기·다시 나누기·수정 요청·답변)은 202 + 작업 id 를 돌려주고, 웹은
 * GET /api/ai-jobs/{jobId}로 끝났는지 본 뒤 GET /api/projects/{id}/review 를 다시 읽는다.
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectDraftService drafts;
    private final RestructureService restructure;
    private final EpisodeDecisionService decisions;

    public ProjectController(ProjectDraftService drafts, RestructureService restructure,
                             EpisodeDecisionService decisions) {
        this.drafts = drafts;
        this.restructure = restructure;
        this.decisions = decisions;
    }

    // ------------------------------------------------------------------ S04 입력

    /** 내 프로젝트 목록 (최근에 만든 것부터) */
    @GetMapping
    public List<Summary> list(@CurrentMemberId Long memberId) {
        return drafts.list(memberId);
    }

    /** 탭의 "추가" — 본문이 비어 있어도 된다 (임시 저장) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Detail create(@CurrentMemberId Long memberId, @Valid @RequestBody(required = false) DraftRequest request) {
        return drafts.create(memberId, request == null ? ProjectDraft.empty() : request.toDraft());
    }

    @GetMapping("/{projectId}")
    public Detail get(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return drafts.get(memberId, projectId);
    }

    /** 자동 임시 저장. version 은 조회 때 받은 값 (다르면 409) */
    @PutMapping("/{projectId}")
    public Detail update(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                         @Valid @RequestBody DraftRequest request) {
        return drafts.update(memberId, projectId, request.toDraft(), request.version());
    }

    /** "기술스택에 없는 기술 추가" — 기술스택에 학습 중으로 만들고 이 프로젝트에 연결 */
    @PostMapping("/{projectId}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSkillView addSkill(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                                  @Valid @RequestBody NewSkillRequest request) {
        return drafts.addSkill(memberId, projectId, request.name(), request.category());
    }

    /** 확정 전 검토 결과 버리기 — S04 에 들어오거나 S05 에서 온보딩 밖으로 나갈 때 (경고 후) */
    @PostMapping("/{projectId}/review/discard")
    public Detail discardReview(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return drafts.discardReview(memberId, projectId);
    }

    /** "[문제]-[해결]-[결과]로 재구성하기" — 지금 원문으로 나누기 */
    @PostMapping("/{projectId}/split")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted split(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return new JobAccepted(restructure.split(memberId, projectId));
    }

    /** 재등록 시작 (S06 "수정") */
    @PostMapping("/{projectId}/reopen")
    public Detail reopen(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return drafts.reopen(memberId, projectId);
    }

    // ------------------------------------------------------------------ S05 검토

    /**
     * 검토 화면 조회. 확정한 프로젝트도 같은 주소로 읽기 모드를 그린다 (기획서 v1.17):
     * {@code status = CONFIRMED} + 확정한 사례만 돌려주고, 아래 변경 API 는 모두 422 다.
     */
    @GetMapping("/{projectId}/review")
    public Review review(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return decisions.review(memberId, projectId);
    }

    /** "다시 나누기" — 피드백 필수 */
    @PostMapping("/{projectId}/resplit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted resplit(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                               @Valid @RequestBody FeedbackRequest request) {
        return new JobAccepted(restructure.resplit(memberId, projectId, request.feedback()));
    }

    /** "이 사례 수정 요청" */
    @PostMapping("/{projectId}/episodes/{episodeId}/revision")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted revise(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                              @PathVariable Long episodeId, @Valid @RequestBody FeedbackRequest request) {
        return new JobAccepted(restructure.revise(memberId, projectId, episodeId, request.feedback()));
    }

    /** 확인 필요 → "답변 추가" */
    @PostMapping("/{projectId}/episodes/{episodeId}/clarifications/{key}/answer")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobAccepted answer(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                              @PathVariable Long episodeId, @PathVariable String key,
                              @Valid @RequestBody AnswerRequest request) {
        return new JobAccepted(restructure.answer(memberId, projectId, episodeId, key, request.answer()));
    }

    /** 확인 필요 → "빼고 진행" (AI 없음) */
    @PostMapping("/{projectId}/episodes/{episodeId}/clarifications/{key}/drop")
    public Review drop(@CurrentMemberId Long memberId, @PathVariable Long projectId, @PathVariable Long episodeId,
                       @PathVariable String key) {
        return decisions.dropClarification(memberId, projectId, episodeId, key);
    }

    /** "직접 편집" — 새 버전 */
    @PutMapping("/{projectId}/episodes/{episodeId}")
    public Review edit(@CurrentMemberId Long memberId, @PathVariable Long projectId, @PathVariable Long episodeId,
                       @Valid @RequestBody EpisodeRequest request) {
        return decisions.editEpisode(memberId, projectId, episodeId, request.toEdit());
    }

    /** "AI가 놓친 사례 직접 추가" */
    @PostMapping("/{projectId}/episodes")
    @ResponseStatus(HttpStatus.CREATED)
    public Review add(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                      @Valid @RequestBody EpisodeRequest request) {
        return decisions.addEpisode(memberId, projectId, request.toEdit());
    }

    /** "이 버전으로 바꾸기" — 고른 버전을 복사해 새 버전 */
    @PostMapping("/{projectId}/episodes/{episodeId}/restore")
    public Review restore(@CurrentMemberId Long memberId, @PathVariable Long projectId, @PathVariable Long episodeId,
                          @Valid @RequestBody RestoreRequest request) {
        return decisions.restoreVersion(memberId, projectId, episodeId, request.versionNo());
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/confirm")
    public Review confirmEpisode(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                                 @PathVariable Long episodeId) {
        return decisions.confirmEpisode(memberId, projectId, episodeId);
    }

    @PostMapping("/{projectId}/episodes/{episodeId}/exclude")
    public Review excludeEpisode(@CurrentMemberId Long memberId, @PathVariable Long projectId,
                                 @PathVariable Long episodeId) {
        return decisions.excludeEpisode(memberId, projectId, episodeId);
    }

    /** "다시 검토하기" (결정 취소) */
    @PostMapping("/{projectId}/episodes/{episodeId}/undo")
    public Review undo(@CurrentMemberId Long memberId, @PathVariable Long projectId, @PathVariable Long episodeId) {
        return decisions.undoDecision(memberId, projectId, episodeId);
    }

    /** "프로젝트 확정" — 확정한 사례만 RAG 에 등록된다 */
    @PostMapping("/{projectId}/confirm")
    public Review confirm(@CurrentMemberId Long memberId, @PathVariable Long projectId) {
        return decisions.confirm(memberId, projectId);
    }

    // ------------------------------------------------------------------ 요청 모양

    /** periodStart·periodEnd 는 "2024-05" 형식. affiliation 이 CAREER 면 careerId 필수 */
    public record DraftRequest(
            @Size(max = ProjectDraft.TITLE_MAX, message = "제목은 100자 이하로 입력해 주세요.") String title,
            YearMonth periodStart,
            YearMonth periodEnd,
            Affiliation affiliation,
            Long careerId,
            @Size(max = ProjectDraft.RAW_TEXT_MAX) String rawText,
            Set<Long> userSkillIds,
            List<String> links,
            Long version) {

        ProjectDraft toDraft() {
            return new ProjectDraft(title, ProjectPeriod.of(periodStart, periodEnd), affiliation, careerId, rawText,
                    userSkillIds, links);
        }
    }

    public record NewSkillRequest(
            @NotBlank(message = "기술 이름을 입력해 주세요.") @Size(max = 100) String name,
            @NotNull(message = "카테고리를 골라 주세요.") SkillCategory category) {
    }

    public record FeedbackRequest(@NotBlank(message = "피드백을 입력해 주세요.") @Size(max = 2000) String feedback) {
    }

    public record AnswerRequest(@NotBlank(message = "답변을 입력해 주세요.") @Size(max = 2000) String answer) {
    }

    public record RestoreRequest(@NotNull @Min(1) Integer versionNo) {
    }

    public record EpisodeRequest(
            @NotBlank(message = "사례 제목을 입력해 주세요.") @Size(max = 100) String title,
            @Size(max = 200) String role,
            @Size(max = 5000) String problem,
            @Size(max = 5000) String solution,
            @Size(max = 5000) String result,
            List<Metric> metrics) {

        EpisodeEdit toEdit() {
            return new EpisodeEdit(title, role, problem, solution, result, metrics);
        }
    }
}
