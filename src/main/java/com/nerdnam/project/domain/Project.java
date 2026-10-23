package com.nerdnam.project.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import com.nerdnam.common.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

/**
 * 프로젝트 — 애그리거트 루트 (02 문서 3장). 사례·사례 버전·보충 답변을 함께 관리하고 불변식 P1~P9 를 지킨다.
 *
 * <p>용어
 * <ul>
 *   <li><b>이번 검토</b>: 프로젝트가 REVIEW 일 때 지금 회차(splitRound)의 사례 (검토 중·확정·제외)</li>
 *   <li><b>확정본</b>: 프로젝트를 확정할 때 확정한 사례. 재등록 중에도 다시 확정할 때까지 남아 검색에 쓰인다 (Q4)</li>
 * </ul>
 * 서비스는 "불러오기 → 이 클래스의 메서드 호출 → 저장"만 한다. 상태 검사는 모두 여기서 한다.
 */
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Embedded
    private ProjectPeriod period;

    @Enumerated(EnumType.STRING)
    @Column(name = "affiliation", nullable = false, length = 20)
    private Affiliation affiliation;

    /** 다른 애그리거트(경력)는 id 로만 참조한다 */
    @Column(name = "career_id")
    private Long careerId;

    /** 사용자가 쓴 원문 그대로 (항상 보관). 보충 답변은 {@link #supplements}에 따로 */
    @Column(name = "raw_text", nullable = false)
    private String rawText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectStatus status;

    /** 나누기 회차: 첫 나누기 = 1, 다시 나누기마다 +1 */
    @Column(name = "split_round", nullable = false)
    private int splitRound;

    /** AI 생성 누적 횟수. 상한 없음, 기록용 (ADR-0002 Q1) */
    @Column(name = "generation_count", nullable = false)
    private int generationCount;

    /** 마지막 확정 시각. 재등록 중(DRAFT/REVIEW)에도 남아 "확정본 있음"을 뜻한다 */
    @Column(name = "last_confirmed_at")
    private Instant lastConfirmedAt;

    @Version
    private long version;

    @ElementCollection
    @CollectionTable(name = "project_skill", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "user_skill_id", nullable = false)
    private Set<Long> userSkillIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "project_link", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "line_no")
    @Column(name = "url", nullable = false, length = 1000)
    private List<String> links = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<ProjectSupplement> supplements = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("splitRound, displayOrder")
    private List<Episode> episodes = new ArrayList<>();

    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    // ------------------------------------------------------------------ 임시 저장 (S04)

    public static Project createDraft(Long memberId, ProjectDraft draft) {
        Project p = new Project();
        p.memberId = Objects.requireNonNull(memberId);
        p.status = ProjectStatus.DRAFT;
        p.apply(draft);
        return p;
    }

    /**
     * 임시 저장. 원문을 고칠 수 있는 것은 초안(DRAFT)일 때뿐이다 (P7-1).
     * 검토 중이면 웹이 S04 에 들어오면서 먼저 {@link #discardReview()}를 부른다.
     */
    public void updateDraft(ProjectDraft draft) {
        ensureNotConfirmed();
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "검토 중인 결과를 정리한 뒤 프로젝트를 고칠 수 있어요.");
        }
        apply(draft);
    }

    /** S04 "기술스택에 없는 기술 추가" — 새로 만든 기술스택 항목을 이 프로젝트에 연결한다 */
    public void addUserSkill(Long userSkillId) {
        ensureNotConfirmed();
        userSkillIds.add(Objects.requireNonNull(userSkillId));
    }

    // ------------------------------------------------------------------ AI 생성 전 검사 (트랜잭션 ①)

    /** 나누기 (S04 "재구성하기") — 지금 원문으로 새로 나눈다. 피드백이 필요 없다 */
    public void checkCanSplit() {
        ensureNotConfirmed();
        if (status != ProjectStatus.DRAFT) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "이미 나눈 결과가 있어요. 다시 나누기를 이용해 주세요.");
        }
        if (title.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "프로젝트 제목을 입력해 주세요.");
        }
        if (rawText.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "프로젝트 본문을 입력해 주세요.");
        }
    }

    /** 다시 나누기 (S05) — 원문은 그대로, 나누는 방식만 바꾼다. 피드백 필수 (기획서 F2) */
    public void checkCanResplit(String feedback) {
        ensureInReview();
        requireText(feedback, "어떻게 다시 나눌지 알려 주세요.");
    }

    /** 수정 요청 (S05) */
    public void checkCanRevise(Long episodeId, String feedback) {
        ensureInReview();
        editableEpisode(episodeId);
        requireText(feedback, "어떻게 고칠지 알려 주세요.");
    }

    /** 확인 질문에 답하기 (S05). 돌려준 질문 문구를 AI 입력에 쓴다 */
    public Clarification checkCanAnswer(Long episodeId, String clarificationKey, String answer) {
        ensureInReview();
        Clarification clarification = editableEpisode(episodeId).current().content().clarification(clarificationKey);
        requireText(answer, "답변을 입력해 주세요.");
        return clarification;
    }

    // ------------------------------------------------------------------ AI 결과 반영 (트랜잭션 ②)

    /**
     * 나누기·다시 나누기 결과 반영 (P3, P4, P9).
     * 이번 검토 사례는 모두 SUPERSEDED, 회차 +1. 재등록 전 확정본은 그대로 둔다 (다시 확정할 때 대체).
     */
    public void applySplit(List<EpisodeContent> drafts, Collection<String> projectSkillNames, AiTrace trace, String feedback) {
        ensureNotConfirmed();   // ①과 ② 사이에 확정됐을 수 있으니 다시 검사
        if (drafts == null || drafts.isEmpty()) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "나눈 사례가 없어요. 본문을 보충한 뒤 다시 시도해 주세요.");
        }
        reviewEpisodes().forEach(Episode::supersede);
        splitRound++;
        int order = 1;
        for (EpisodeContent draft : drafts) {
            // 다시 나누기 피드백은 새 사례의 첫 버전에 남긴다 (어떤 요청으로 나왔는지)
            episodes.add(Episode.create(this, splitRound, order++, EpisodeOrigin.AI,
                    draft.limitSkillsTo(projectSkillNames), ChangeType.AI_SPLIT, feedback, trace));
        }
        generationCount++;
        status = ProjectStatus.REVIEW;
    }

    /** 수정 요청 결과 반영 — 그 사례의 새 버전 (P3, P9) */
    public void applyRevision(Long episodeId, EpisodeContent revised, Collection<String> projectSkillNames,
                              AiTrace trace, String feedback) {
        ensureInReview();
        editableEpisode(episodeId).addVersion(revised.limitSkillsTo(projectSkillNames), ChangeType.AI_REVISION,
                null, feedback, trace);
        generationCount++;
    }

    /**
     * 확인 질문 답변 반영 — 답변을 보충 답변으로 저장하고(원문 뒤에 붙는다) AI 가 만든 새 버전을 쌓는다.
     * AI 가 같은 질문을 다시 돌려줘도 답한 질문은 뺀다.
     */
    public void applyAnswer(Long episodeId, String clarificationKey, String answer, EpisodeContent revised,
                            Collection<String> projectSkillNames, AiTrace trace) {
        Clarification clarification = checkCanAnswer(episodeId, clarificationKey, answer);
        Episode episode = editableEpisode(episodeId);
        supplements.add(ProjectSupplement.of(this, episode.getId(), clarification.question(), answer));
        EpisodeContent next = revised.limitSkillsTo(projectSkillNames);
        boolean stillAsked = next.clarifications().stream().anyMatch(c -> Objects.equals(c.key(), clarificationKey));
        episode.addVersion(stillAsked ? next.withoutClarification(clarificationKey) : next,
                ChangeType.AI_REVISION, null, null, trace);
        generationCount++;
    }

    // ------------------------------------------------------------------ AI 없이 고치기

    /** "빼고 진행" (Q10) — 그 질문을 뺀 새 버전, AI 호출 없음 */
    public void dropClarification(Long episodeId, String clarificationKey) {
        ensureInReview();
        Episode episode = editableEpisode(episodeId);
        episode.addVersion(episode.current().content().withoutClarification(clarificationKey),
                ChangeType.USER_EDIT, null, null, null);
    }

    /** 직접 편집 — 새 버전(USER_EDIT). 사용자가 쓴 내용이라 확인 필요로 다시 잡지 않는다 */
    public void editEpisode(Long episodeId, EpisodeEdit edit) {
        ensureInReview();
        Episode episode = editableEpisode(episodeId);
        episode.addVersion(episode.current().content().edited(edit), ChangeType.USER_EDIT, null, null, null);
    }

    /** 사례 직접 추가 (AI 가 놓친 사례) — 이번 회차에 v1(USER_CREATED)로 */
    public Episode addEpisode(EpisodeEdit content) {
        ensureInReview();
        int order = reviewEpisodes().stream().mapToInt(Episode::getDisplayOrder).max().orElse(0) + 1;
        Episode episode = Episode.create(this, splitRound, order, EpisodeOrigin.USER, content.toNewContent(),
                ChangeType.USER_CREATED, null, null);
        episodes.add(episode);
        return episode;
    }

    /** "이 버전으로 바꾸기" (P6) — 고른 버전을 복사해 새 최신 버전(RESTORED). 사례 상태는 그대로 */
    public void restoreVersion(Long episodeId, int versionNo) {
        ensureInReview();
        Episode episode = editableEpisode(episodeId);
        if (versionNo == episode.getCurrentVersion()) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "이미 지금 버전이에요.");
        }
        EpisodeContent chosen = episode.version(versionNo).content();
        episode.addVersion(chosen, ChangeType.RESTORED, versionNo, null, null);
    }

    // ------------------------------------------------------------------ 결정

    /** 사례 확정 (P2): 확인 필요가 남아 있으면 안 된다 */
    public void confirmEpisode(Long episodeId) {
        ensureInReview();
        Episode episode = reviewEpisode(episodeId);
        if (episode.current().content().hasOpenClarifications()) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확인 필요 항목에 답하거나 빼야 확정할 수 있어요.");
        }
        episode.confirm();
    }

    /** 사례 제외 — 저장(RAG 등록)하지 않는다 */
    public void excludeEpisode(Long episodeId) {
        ensureInReview();
        reviewEpisode(episodeId).exclude();
    }

    /** 다시 검토하기 (P7-2): 확정·제외 → 검토 중. 프로젝트 확정 전에만 */
    public void undoDecision(Long episodeId) {
        ensureInReview();
        Episode episode = reviewEpisode(episodeId);
        if (!episode.getStatus().isDecided()) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "이미 검토 중인 사례예요.");
        }
        episode.backToReview();
    }

    /**
     * 프로젝트 확정 (P1): 이번 검토 사례가 모두 결정됐고 확정이 1개 이상.
     * 재등록이었다면 이전 확정본(이전 회차의 확정·제외 사례)을 SUPERSEDED 로 바꾼다.
     */
    public void confirm() {
        ensureInReview();
        List<Episode> review = reviewEpisodes();
        boolean allDecided = review.stream().allMatch(e -> e.getStatus().isDecided());
        boolean anyConfirmed = review.stream().anyMatch(Episode::isConfirmed);
        if (!allDecided || !anyConfirmed) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "모든 사례를 확정하거나 제외하고, 1개 이상 확정해야 해요.");
        }
        episodes.stream()
                .filter(e -> e.getSplitRound() < splitRound && e.getStatus().isDecided())
                .forEach(Episode::supersede);
        status = ProjectStatus.CONFIRMED;
        lastConfirmedAt = Instant.now();
        domainEvents.add(new ProjectConfirmed(id, memberId));
    }

    /** 확정 전 검토 결과 버리기 (P7-1) — 여러 번 불러도 안전. 재등록 중이면 이전 확정본은 그대로 */
    public void discardReview() {
        if (status != ProjectStatus.REVIEW) {
            return;
        }
        reviewEpisodes().forEach(Episode::discard);
        status = ProjectStatus.DRAFT;
    }

    /** 재등록 시작 (Q6) — 내용은 지우지 않는다. 이전 확정본은 다음 나누기의 맥락이 되고, 다시 확정할 때까지 검색에 쓰인다 */
    public void reopen() {
        if (status != ProjectStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확정된 프로젝트만 다시 등록할 수 있어요.");
        }
        status = ProjectStatus.DRAFT;
        domainEvents.add(new ProjectReopened(id, memberId));
    }

    // ------------------------------------------------------------------ 조회

    /** AI 에 보낼 원문 = 원문 + 보충 답변 (버린 사례의 답변은 뺀다, P7-1). 뒤에만 붙으므로 원문 부분의 위치는 그대로다 */
    public String sourceText() {
        return sourceTextWith(null, null);
    }

    /** 아직 저장하지 않은 답변 하나를 덧붙인 원문 (확인 질문 답변을 AI 에 보낼 때) */
    public String sourceTextWith(String question, String answer) {
        Set<Long> discarded = new HashSet<>();
        episodes.stream().filter(e -> e.getStatus() == EpisodeStatus.DISCARDED).forEach(e -> discarded.add(e.getId()));
        Stream<String> parts = supplements.stream()
                .filter(s -> s.getEpisodeId() == null || !discarded.contains(s.getEpisodeId()))
                .map(ProjectSupplement::asSourceText);
        if (question != null) {
            parts = Stream.concat(parts, Stream.of("질문: " + question + "\n답변: " + answer.strip()));
        }
        List<String> appended = parts.toList();
        return appended.isEmpty() ? rawText : rawText + "\n\n" + String.join("\n\n", appended);
    }

    /** 원문 화면(S04)·검토 화면(S05)에 보여줄 보충 답변 (버린 사례의 답변 제외) */
    public List<ProjectSupplement> visibleSupplements() {
        Set<Long> discarded = new HashSet<>();
        episodes.stream().filter(e -> e.getStatus() == EpisodeStatus.DISCARDED).forEach(e -> discarded.add(e.getId()));
        return supplements.stream().filter(s -> s.getEpisodeId() == null || !discarded.contains(s.getEpisodeId())).toList();
    }

    /** 이번 검토 사례 (S05 화면). 검토 중이 아니면 비어 있다 */
    public List<Episode> reviewEpisodes() {
        if (status != ProjectStatus.REVIEW) {
            return List.of();
        }
        return episodes.stream()
                .filter(e -> e.getSplitRound() == splitRound)
                .filter(e -> e.getStatus() == EpisodeStatus.REVIEW || e.getStatus().isDecided())
                .toList();
    }

    /**
     * 검토 화면(S05)에 보여줄 사례: 검토 중이면 이번 검토 사례, 확정이면 <b>확정한 사례만</b>(읽기 모드,
     * 기획서 v1.17 F2), 초안이면 없음. 확정 상태에서 제외한 사례는 저장(RAG)도 되지 않으므로 보여 주지 않는다.
     *
     * <p>재등록 중(초안)이면 비어 있다. 이전 확정 사례는 검색에 계속 쓰이지만(Q4) 재등록 중에는 원문을 고칠 수
     * 있어 그 사례의 {@code sourceSpans}가 지금 원문과 맞지 않는다. 그래서 읽기 모드로 보여 주지 않고
     * S06 "재등록 중" 카드의 요약으로만 보여 준다 (02 문서 8장 "S08 2 (재등록 중)" — FE·기획 확인 필요).
     */
    public List<Episode> visibleEpisodes() {
        if (status == ProjectStatus.REVIEW) {
            return reviewEpisodes();
        }
        if (status == ProjectStatus.CONFIRMED) {
            return confirmedEpisodes();
        }
        return List.of();
    }

    /**
     * 확정본 사례 — 검색(RAG)에 쓰이는 사례. 확정 상태면 지금 확정한 사례,
     * 재등록 중이면 이전에 확정한 사례 (다시 확정할 때까지 유지, Q4).
     */
    public List<Episode> confirmedEpisodes() {
        return episodes.stream()
                .filter(Episode::isConfirmed)
                .filter(e -> !(status == ProjectStatus.REVIEW && e.getSplitRound() == splitRound))
                .toList();
    }

    /** 재등록 때 AI 에 보낼 이전 확정 사례 (맥락) */
    public List<EpisodeContent> previouslyConfirmedContents() {
        if (status == ProjectStatus.CONFIRMED) {
            return List.of();
        }
        return confirmedEpisodes().stream().map(e -> e.current().content()).toList();
    }

    /** 확정본이 있는데 다시 등록하는 중 (S06 "재등록 중") */
    public boolean isReregistering() {
        return lastConfirmedAt != null && status != ProjectStatus.CONFIRMED;
    }

    public Episode episode(Long episodeId) {
        return episodes.stream()
                .filter(e -> Objects.equals(e.getId(), episodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사례를 찾을 수 없어요."));
    }

    public List<Episode> getEpisodes() {
        return Collections.unmodifiableList(episodes);
    }

    /** 기간 칸이 모두 비어 있으면 Hibernate 가 임베디드 값을 null 로 읽으므로 빈 기간으로 돌려준다 */
    public ProjectPeriod getPeriod() {
        return period == null ? ProjectPeriod.empty() : period;
    }

    public List<ProjectSupplement> getSupplements() {
        return Collections.unmodifiableList(supplements);
    }

    public Set<Long> getUserSkillIds() {
        return Collections.unmodifiableSet(userSkillIds);
    }

    public List<String> getLinks() {
        return Collections.unmodifiableList(links);
    }

    @DomainEvents
    Collection<Object> domainEvents() {
        return List.copyOf(domainEvents);
    }

    @AfterDomainEventPublication
    void clearDomainEvents() {
        domainEvents.clear();
    }

    // ------------------------------------------------------------------ 내부

    private void apply(ProjectDraft draft) {
        this.title = draft.title();
        this.period = draft.period();
        this.affiliation = draft.affiliation();
        this.careerId = draft.careerId();
        this.rawText = draft.rawText();
        this.userSkillIds.clear();
        this.userSkillIds.addAll(draft.userSkillIds());
        this.links.clear();
        this.links.addAll(draft.links());
    }

    /** 이번 검토 사례 중 하나 */
    private Episode reviewEpisode(Long episodeId) {
        return reviewEpisodes().stream()
                .filter(e -> Objects.equals(e.getId(), episodeId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "검토 중인 사례를 찾을 수 없어요."));
    }

    /** 내용을 바꿀 수 있는 사례 = 이번 검토의 "검토 중" 사례. 확정·제외한 사례는 다시 검토하기부터 */
    private Episode editableEpisode(Long episodeId) {
        Episode episode = reviewEpisode(episodeId);
        if (episode.getStatus() != EpisodeStatus.REVIEW) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확정·제외한 사례는 다시 검토하기를 누른 뒤 고칠 수 있어요.");
        }
        return episode;
    }

    private void ensureInReview() {
        ensureNotConfirmed();
        if (status != ProjectStatus.REVIEW) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "검토 중인 프로젝트가 아니에요. 먼저 사례로 나눠 주세요.");
        }
    }

    /** P7: 확정된 프로젝트는 재등록부터 */
    private void ensureNotConfirmed() {
        if (status == ProjectStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확정된 프로젝트예요. 다시 등록을 시작한 뒤 수정해 주세요.");
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, message);
        }
    }
}
