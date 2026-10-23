package com.nerdnam.project.domain;

import static com.nerdnam.project.domain.ProjectFixture.SKILLS;
import static com.nerdnam.project.domain.ProjectFixture.assignIds;
import static com.nerdnam.project.domain.ProjectFixture.confirmedWithFirstEpisode;
import static com.nerdnam.project.domain.ProjectFixture.content;
import static com.nerdnam.project.domain.ProjectFixture.draftProject;
import static com.nerdnam.project.domain.ProjectFixture.question;
import static com.nerdnam.project.domain.ProjectFixture.reviewing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nerdnam.common.error.BusinessException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Project 애그리거트의 불변식 P1~P9 (02 문서 3.3) */
class ProjectTest {

    private static final AiTrace TRACE = new AiTrace("qwen", "p1-v1");

    @Nested
    @DisplayName("임시 저장 (S04)")
    class Draft {

        @Test
        void 소속이_경력이면_경력을_골라야_한다_P8() {
            assertThatThrownBy(() -> new ProjectDraft("제목", null, Affiliation.CAREER, null, "", Set.of(), List.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("경력을 선택");
        }

        @Test
        void 개인_프로젝트로_바꾸면_경력_연결이_빠진다() {
            ProjectDraft draft = new ProjectDraft("제목", null, Affiliation.PERSONAL, 7L, "", Set.of(), List.of());

            assertThat(draft.careerId()).isNull();
        }

        @Test
        void 빈_링크_줄은_버린다() {
            ProjectDraft draft = new ProjectDraft("제목", null, null, null, "", Set.of(), List.of("  ", "https://a.b"));

            assertThat(draft.links()).containsExactly("https://a.b");
            assertThat(draft.affiliation()).isEqualTo(Affiliation.PERSONAL);
        }

        @Test
        void 검토_중에는_원문을_고칠_수_없다_P7_1() {
            Project project = reviewing();

            assertThatThrownBy(() -> project.updateDraft(ProjectFixture.draft()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("검토 중인 결과를 정리한 뒤");
        }

        @Test
        void 본문이_없으면_나눌_수_없다() {
            Project project = Project.createDraft(1L, new ProjectDraft("제목", null, null, null, "  ", Set.of(), List.of()));

            assertThatThrownBy(project::checkCanSplit).hasMessageContaining("본문을 입력");
        }
    }

    @Nested
    @DisplayName("나누기·다시 나누기 (P3, P4, P9)")
    class Split {

        @Test
        void 나누면_검토_중이_되고_회차와_생성_기록이_1씩_오른다() {
            Project project = reviewing();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.REVIEW);
            assertThat(project.getSplitRound()).isEqualTo(1);
            assertThat(project.getGenerationCount()).isEqualTo(1);
            assertThat(project.reviewEpisodes()).extracting(Episode::getDisplayOrder).containsExactly(1, 2);
            assertThat(project.reviewEpisodes().getFirst().current().getChangeType()).isEqualTo(ChangeType.AI_SPLIT);
        }

        @Test
        void 다시_나누면_이번_사례는_대체되고_결정이_초기화된다_P4() {
            Project project = reviewing();
            Episode first = project.reviewEpisodes().getFirst();
            project.confirmEpisode(first.getId());

            project.checkCanResplit("두 사례를 합쳐 줘");
            project.applySplit(List.of(content("합친 사례")), SKILLS, TRACE, "두 사례를 합쳐 줘");
            assignIds(project);

            assertThat(first.getStatus()).isEqualTo(EpisodeStatus.SUPERSEDED);
            assertThat(project.getSplitRound()).isEqualTo(2);
            assertThat(project.getGenerationCount()).isEqualTo(2);
            assertThat(project.reviewEpisodes()).singleElement()
                    .satisfies(e -> {
                        assertThat(e.getStatus()).isEqualTo(EpisodeStatus.REVIEW);
                        assertThat(e.current().getFeedback()).isEqualTo("두 사례를 합쳐 줘");
                    });
        }

        @Test
        void 다시_나누기는_피드백이_있어야_한다() {
            Project project = reviewing();

            assertThatThrownBy(() -> project.checkCanResplit(" ")).hasMessageContaining("어떻게 다시 나눌지");
        }

        @Test
        void 생성_횟수에는_상한이_없다_P3() {
            Project project = reviewing();
            for (int i = 0; i < 30; i++) {
                project.applySplit(List.of(content("사례")), SKILLS, TRACE, "다르게");
            }

            assertThat(project.getGenerationCount()).isEqualTo(31);
        }

        @Test
        void AI가_준_사용_기술은_프로젝트_사용_기술_안의_것만_남고_표준_이름으로_맞춘다_P9() {
            EpisodeContent ai = new EpisodeContent("사례", null, "", "", "", List.of("spring boot", "Django"),
                    null, null, null);

            Project project = reviewing(ai);

            assertThat(project.reviewEpisodes().getFirst().current().getSkills()).containsExactly("Spring Boot");
        }

        @Test
        void 나눈_사례가_없으면_반영하지_않는다() {
            Project project = draftProject();

            assertThatThrownBy(() -> project.applySplit(List.of(), SKILLS, TRACE, null))
                    .hasMessageContaining("나눈 사례가 없어요");
            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("사례 고치기 (P5, P6, Q10)")
    class Edit {

        @Test
        void 수정_요청은_새_버전을_쌓는다_P5() {
            Project project = reviewing();
            Episode episode = project.reviewEpisodes().getFirst();

            project.applyRevision(episode.getId(), content("고친 사례"), SKILLS, TRACE, "더 짧게");

            assertThat(episode.getCurrentVersion()).isEqualTo(2);
            assertThat(episode.getVersions()).extracting(EpisodeVersion::getVersionNo).containsExactly(1, 2);
            assertThat(episode.current().getTitle()).isEqualTo("고친 사례");
            assertThat(episode.current().getFeedback()).isEqualTo("더 짧게");
            assertThat(episode.version(1).getTitle()).isEqualTo("사례 A");
        }

        @Test
        void 이_버전으로_바꾸기는_고른_버전을_복사한_새_버전이고_상태는_그대로다_P6() {
            Project project = reviewing(content("사례", question("q1")));
            Episode episode = project.reviewEpisodes().getFirst();
            project.dropClarification(episode.getId(), "q1");

            project.restoreVersion(episode.getId(), 1);

            assertThat(episode.getCurrentVersion()).isEqualTo(3);
            assertThat(episode.current().getChangeType()).isEqualTo(ChangeType.RESTORED);
            assertThat(episode.current().getRestoredFrom()).isEqualTo(1);
            assertThat(episode.current().content().clarifications()).extracting(Clarification::key).containsExactly("q1");
            assertThat(episode.getStatus()).isEqualTo(EpisodeStatus.REVIEW);
        }

        @Test
        void 지금_버전으로는_바꿀_수_없다() {
            Project project = reviewing();
            Episode episode = project.reviewEpisodes().getFirst();

            assertThatThrownBy(() -> project.restoreVersion(episode.getId(), 1)).hasMessageContaining("이미 지금 버전");
        }

        @Test
        void 없는_버전으로는_바꿀_수_없다() {
            Project project = reviewing();
            Episode episode = project.reviewEpisodes().getFirst();

            assertThatThrownBy(() -> project.restoreVersion(episode.getId(), 9)).hasMessageContaining("버전을 찾을 수 없어요");
        }

        @Test
        void 빼고_진행은_그_질문만_뺀_새_버전이다_Q10() {
            Project project = reviewing(content("사례", question("q1"), question("q2")));
            Episode episode = project.reviewEpisodes().getFirst();

            project.dropClarification(episode.getId(), "q1");

            assertThat(episode.current().getChangeType()).isEqualTo(ChangeType.USER_EDIT);
            assertThat(episode.current().content().clarifications()).extracting(Clarification::key).containsExactly("q2");
        }

        @Test
        void 직접_편집은_확인_질문과_원문_구간을_그대로_둔다() {
            Project project = reviewing(content("사례", question("q1")));
            Episode episode = project.reviewEpisodes().getFirst();

            project.editEpisode(episode.getId(), new EpisodeEdit("새 제목", "리드", "문제", "해결", "결과", null));

            EpisodeContent now = episode.current().content();
            assertThat(now.title()).isEqualTo("새 제목");
            assertThat(now.clarifications()).hasSize(1);
            assertThat(now.sourceSpans()).containsExactly(new SourceSpan(0, 20));
            assertThat(now.metrics()).hasSize(1);
        }

        @Test
        void 확정한_사례는_다시_검토하기_전에는_고칠_수_없다() {
            Project project = reviewing();
            Episode episode = project.reviewEpisodes().getFirst();
            project.confirmEpisode(episode.getId());

            assertThatThrownBy(() -> project.editEpisode(episode.getId(), new EpisodeEdit("t", null, "", "", "", null)))
                    .hasMessageContaining("다시 검토하기를 누른 뒤");
        }

        @Test
        void 사례를_직접_추가하면_이번_회차의_마지막에_붙는다() {
            Project project = reviewing();

            Episode added = project.addEpisode(new EpisodeEdit("놓친 사례", null, "문제", "해결", "결과", List.of()));

            assertThat(added.getOrigin()).isEqualTo(EpisodeOrigin.USER);
            assertThat(added.getSplitRound()).isEqualTo(1);
            assertThat(added.getDisplayOrder()).isEqualTo(3);
            assertThat(added.current().getChangeType()).isEqualTo(ChangeType.USER_CREATED);
            assertThat(project.reviewEpisodes()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("확인 질문 답변과 원문 (03 문서 3.4)")
    class Answer {

        @Test
        void 답변은_원문_뒤에_붙고_원문은_그대로다() {
            Project project = reviewing(content("사례", question("q1")));
            Episode episode = project.reviewEpisodes().getFirst();
            String raw = project.getRawText();

            project.applyAnswer(episode.getId(), "q1", "1.2초에서 0.3초", content("사례", question("q1")), SKILLS, TRACE);

            assertThat(project.getRawText()).isEqualTo(raw);
            assertThat(project.sourceText()).startsWith(raw)
                    .endsWith("질문: 응답 속도 개선 수치가 있나요?\n답변: 1.2초에서 0.3초");
            assertThat(episode.current().content().hasOpenClarifications())
                    .as("AI 가 같은 질문을 다시 돌려줘도 답한 질문은 뺀다").isFalse();
            assertThat(project.getGenerationCount()).isEqualTo(2);
        }

        @Test
        void 없는_질문에는_답할_수_없다() {
            Project project = reviewing(content("사례", question("q1")));
            Episode episode = project.reviewEpisodes().getFirst();

            assertThatThrownBy(() -> project.checkCanAnswer(episode.getId(), "nope", "답"))
                    .hasMessageContaining("확인 질문을 찾을 수 없어요");
        }

        @Test
        void 버린_사례의_답변은_원문_조합에서_빠진다_P7_1() {
            Project project = reviewing(content("사례", question("q1")));
            Episode episode = project.reviewEpisodes().getFirst();
            project.applyAnswer(episode.getId(), "q1", "답", content("사례"), SKILLS, TRACE);

            project.discardReview();

            assertThat(project.sourceText()).isEqualTo(project.getRawText());
            assertThat(project.visibleSupplements()).isEmpty();
        }
    }

    @Nested
    @DisplayName("결정과 확정 (P1, P2, P7)")
    class Decide {

        @Test
        void 확인_필요가_남은_사례는_확정할_수_없다_P2() {
            Project project = reviewing(content("사례", question("q1")));
            Long episodeId = project.reviewEpisodes().getFirst().getId();

            assertThatThrownBy(() -> project.confirmEpisode(episodeId))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("확인 필요");
        }

        @Test
        void 결정하지_않은_사례가_있으면_프로젝트를_확정할_수_없다_P1() {
            Project project = reviewing();
            project.confirmEpisode(project.reviewEpisodes().getFirst().getId());

            assertThatThrownBy(project::confirm).hasMessageContaining("모든 사례를 확정하거나 제외");
        }

        @Test
        void 모두_제외하면_프로젝트를_확정할_수_없다_P1() {
            Project project = reviewing();
            project.reviewEpisodes().forEach(e -> project.excludeEpisode(e.getId()));

            assertThatThrownBy(project::confirm).hasMessageContaining("1개 이상 확정");
        }

        @Test
        void 확정하면_확정_이벤트가_쌓인다() {
            Project project = confirmedWithFirstEpisode();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.CONFIRMED);
            assertThat(project.getLastConfirmedAt()).isNotNull();
            assertThat(project.domainEvents()).singleElement().isInstanceOf(ProjectConfirmed.class);
            assertThat(project.confirmedEpisodes()).extracting(e -> e.current().getTitle()).containsExactly("사례 A");
        }

        @Test
        void 확정한_프로젝트의_읽기_모드는_확정한_사례만_보여준다_v1_17() {
            Project project = confirmedWithFirstEpisode();

            assertThat(project.visibleEpisodes())
                    .as("제외한 사례 B 는 저장도 되지 않으므로 읽기 모드에서 빼고 보여준다")
                    .extracting(e -> e.current().getTitle())
                    .containsExactly("사례 A");
        }

        @Test
        void 검토_중_읽기_모드_전에는_결정한_사례도_함께_보여준다() {
            Project project = reviewing();
            project.excludeEpisode(project.reviewEpisodes().getLast().getId());

            assertThat(project.visibleEpisodes()).extracting(e -> e.current().getTitle())
                    .containsExactly("사례 A", "사례 B");
        }

        @Test
        void 다시_검토하기는_결정을_검토_중으로_되돌린다_P7_2() {
            Project project = reviewing();
            Episode episode = project.reviewEpisodes().getFirst();
            project.excludeEpisode(episode.getId());

            project.undoDecision(episode.getId());

            assertThat(episode.getStatus()).isEqualTo(EpisodeStatus.REVIEW);
            assertThat(episode.getDecidedAt()).isNull();
        }

        @Test
        void 확정된_프로젝트는_아무것도_바꿀_수_없다_P7() {
            Project project = confirmedWithFirstEpisode();
            Long episodeId = project.confirmedEpisodes().getFirst().getId();

            assertThatThrownBy(() -> project.undoDecision(episodeId)).hasMessageContaining("확정된 프로젝트예요");
            assertThatThrownBy(() -> project.updateDraft(ProjectFixture.draft())).hasMessageContaining("확정된 프로젝트예요");
            assertThatThrownBy(project::checkCanSplit).hasMessageContaining("확정된 프로젝트예요");
        }
    }

    @Nested
    @DisplayName("검토 결과 버리기와 재등록 (P7-1, Q6)")
    class DiscardAndReopen {

        @Test
        void 검토_결과를_버리면_이번_사례는_DISCARDED_프로젝트는_초안이_된다() {
            Project project = reviewing();
            List<Episode> review = project.reviewEpisodes();

            project.discardReview();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(review).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(EpisodeStatus.DISCARDED));
            assertThat(project.reviewEpisodes()).isEmpty();
        }

        @Test
        void 버릴_것이_없으면_아무_일도_없다() {
            Project project = draftProject();

            project.discardReview();
            project.discardReview();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        }

        @Test
        void 재등록하면_초안이_되고_이전_확정본은_맥락으로_남는다() {
            Project project = confirmedWithFirstEpisode();

            project.reopen();

            assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);
            assertThat(project.isReregistering()).isTrue();
            assertThat(project.previouslyConfirmedContents()).extracting(EpisodeContent::title).containsExactly("사례 A");
            assertThat(project.confirmedEpisodes()).as("다시 확정할 때까지 검색에 쓰인다").hasSize(1);
        }

        @Test
        void 재등록_중_나누어도_이전_확정본은_그대로다() {
            Project project = confirmedWithFirstEpisode();
            Episode previous = project.confirmedEpisodes().getFirst();
            project.reopen();
            project.checkCanSplit();

            project.applySplit(List.of(content("새 사례")), SKILLS, TRACE, null);
            assignIds(project);

            assertThat(previous.getStatus()).isEqualTo(EpisodeStatus.CONFIRMED);
            assertThat(project.reviewEpisodes()).extracting(e -> e.current().getTitle()).containsExactly("새 사례");
            assertThat(project.confirmedEpisodes()).containsExactly(previous);
        }

        @Test
        void 재등록_중_검토를_버려도_이전_확정본은_남는다() {
            Project project = confirmedWithFirstEpisode();
            Episode previous = project.confirmedEpisodes().getFirst();
            project.reopen();
            project.applySplit(List.of(content("새 사례")), SKILLS, TRACE, null);
            assignIds(project);

            project.discardReview();

            assertThat(previous.getStatus()).isEqualTo(EpisodeStatus.CONFIRMED);
            assertThat(project.confirmedEpisodes()).containsExactly(previous);
        }

        @Test
        void 다시_확정하면_이전_확정본은_SUPERSEDED가_된다_P1() {
            Project project = confirmedWithFirstEpisode();
            List<Episode> previous = List.copyOf(project.getEpisodes());
            project.reopen();
            project.applySplit(List.of(content("새 사례")), SKILLS, TRACE, null);
            assignIds(project);
            Episode fresh = project.reviewEpisodes().getFirst();
            project.confirmEpisode(fresh.getId());

            project.confirm();

            assertThat(previous).allSatisfy(e -> assertThat(e.getStatus()).isEqualTo(EpisodeStatus.SUPERSEDED));
            assertThat(project.confirmedEpisodes()).containsExactly(fresh);
            assertThat(project.previouslyConfirmedContents()).isEmpty();
        }

        @Test
        void 확정되지_않은_프로젝트는_재등록할_수_없다() {
            Project project = reviewing();

            assertThatThrownBy(project::reopen).hasMessageContaining("확정된 프로젝트만");
        }
    }
}
