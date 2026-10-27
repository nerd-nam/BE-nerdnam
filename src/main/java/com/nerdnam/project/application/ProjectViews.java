package com.nerdnam.project.application;

import com.nerdnam.project.domain.Affiliation;
import com.nerdnam.project.domain.ChangeType;
import com.nerdnam.project.domain.Clarification;
import com.nerdnam.project.domain.Episode;
import com.nerdnam.project.domain.EpisodeContent;
import com.nerdnam.project.domain.EpisodeOrigin;
import com.nerdnam.project.domain.EpisodeStatus;
import com.nerdnam.project.domain.EpisodeVersion;
import com.nerdnam.project.domain.Metric;
import com.nerdnam.project.domain.Project;
import com.nerdnam.project.domain.ProjectStatus;
import com.nerdnam.project.domain.ProjectSupplement;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** 프로젝트 조회 결과들. 트랜잭션 안에서 엔티티를 이 값으로 바꿔 돌려준다 */
public final class ProjectViews {

    private ProjectViews() {
    }

    /** 사용 기술 하나 (기술스택 항목 id + 표준 이름) */
    public record SkillRef(Long userSkillId, String name) {
    }

    /** 목록 (S04 위쪽 탭, S06) */
    public record Summary(Long id, String title, ProjectStatus status, boolean reregistering, Affiliation affiliation,
                          @Nullable Long careerId, @Nullable YearMonth periodStart, @Nullable YearMonth periodEnd,
                          int confirmedEpisodeCount, @Nullable Instant lastConfirmedAt, Instant updatedAt) {

        static Summary from(Project p) {
            return new Summary(p.getId(), p.getTitle(), p.getStatus(), p.isReregistering(), p.getAffiliation(),
                    p.getCareerId(), p.getPeriod().startMonth(), p.getPeriod().endMonth(),
                    p.confirmedEpisodes().size(), p.getLastConfirmedAt(), p.getUpdatedAt());
        }
    }

    /** 입력 화면 (S04). 재등록 중이면 이전 확인 답변도 채워져 온다 */
    public record Detail(Long id, String title, ProjectStatus status, boolean reregistering, Affiliation affiliation,
                         @Nullable Long careerId, @Nullable String careerCompanyName, @Nullable YearMonth periodStart,
                         @Nullable YearMonth periodEnd, String rawText, List<SkillRef> skills, List<String> links,
                         List<SupplementView> supplements, int splitRound, int generationCount, long version,
                         @Nullable Long runningJobId) {
    }

    /**
     * 검토 화면 (S05). {@code status}가 {@code CONFIRMED}면 웹은 <b>읽기 모드</b>로 그린다 (기획서 v1.17 F2):
     * {@code episodes}에 확정한 사례만 담겨 오고, 결정·수정 요청·다시 나누기·확인 질문·버전 바꾸기 API 는
     * 모두 422 로 막힌다. "수정"은 재등록({@code POST /reopen}) 뒤 S04 로 간다.
     */
    public record Review(Long id, String title, ProjectStatus status, boolean reregistering, Affiliation affiliation,
                         @Nullable String careerCompanyName, @Nullable YearMonth periodStart,
                         @Nullable YearMonth periodEnd, String rawText, String sourceText,
                         List<SupplementView> supplements, List<SkillRef> skills, int splitRound, int generationCount,
                         List<EpisodeView> episodes, @Nullable Long runningJobId) {
    }

    public record SupplementView(Long id, @Nullable Long episodeId, String question, String answer,
                                 Instant createdAt) {

        static SupplementView from(ProjectSupplement s) {
            return new SupplementView(s.getId(), s.getEpisodeId(), s.getQuestion(), s.getAnswer(), s.getCreatedAt());
        }
    }

    /**
     * 원문 구간 — <b>응답은 {@code {"start":0,"end":11}} 객체</b>다 (계약 C-03, 웹이 형광펜을 칠하는 값).
     * 계약 스키마 이름을 그대로 두려고 도메인 타입과 같은 이름을 쓴다 (FE 가 이 이름으로 타입을 만든다).
     *
     * <p>도메인 {@code project.domain.SourceSpan} 에는 {@code @JsonFormat(shape = ARRAY)} 가 붙어 있다. jsonb 컬럼에
     * 기획서 4.3 처럼 {@code [0, 182]} 로 저장하려는 설정인데, 그 타입을 응답에 그대로 내보내면
     * <b>응답도 배열이 된다</b> — 계약은 객체여서 웹의 형광펜이 전부 칠해지지 않았다 (BE #37).
     * 그래서 저장용 타입과 응답용 타입을 나눈다.
     */
    public record SourceSpan(int start, int end) {

        static SourceSpan from(com.nerdnam.project.domain.SourceSpan span) {
            return new SourceSpan(span.start(), span.end());
        }
    }

    /** 사례 + 현재 내용 + 모든 버전 (버전 탭은 웹에서 고른다, C-02 S05 조회 2) */
    public record EpisodeView(Long id, int splitRound, int displayOrder, EpisodeOrigin origin, EpisodeStatus status,
                              int currentVersion, String title, @Nullable String role, @Nullable String problem,
                              @Nullable String solution, @Nullable String result, List<String> skills,
                              List<Metric> metrics, List<SourceSpan> sourceSpans, List<Clarification> clarifications,
                              List<VersionView> versions) {

        static EpisodeView from(Episode e) {
            EpisodeContent c = e.current().content();
            return new EpisodeView(e.getId(), e.getSplitRound(), e.getDisplayOrder(), e.getOrigin(), e.getStatus(),
                    e.getCurrentVersion(), c.title(), c.role(), c.problem(), c.solution(), c.result(), c.skills(),
                    c.metrics(), c.sourceSpans().stream().map(SourceSpan::from).toList(), c.clarifications(),
                    e.getVersions().stream().map(VersionView::from).toList());
        }
    }

    public record VersionView(int versionNo, ChangeType changeType, @Nullable Integer restoredFrom,
                              @Nullable String feedback, Instant createdAt, String title, @Nullable String role,
                              @Nullable String problem, @Nullable String solution, @Nullable String result,
                              List<String> skills, List<Metric> metrics, List<SourceSpan> sourceSpans,
                              List<Clarification> clarifications) {

        static VersionView from(EpisodeVersion v) {
            return new VersionView(v.getVersionNo(), v.getChangeType(), v.getRestoredFrom(), v.getFeedback(),
                    v.getCreatedAt(), v.getTitle(), v.getRole(), v.getProblem(), v.getSolution(), v.getResult(),
                    v.getSkills(), v.getMetrics(),
                    v.getSourceSpans().stream().map(SourceSpan::from).toList(), v.getClarifications());
        }
    }
}
