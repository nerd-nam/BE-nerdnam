package com.nerdnam.project.domain;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.test.util.ReflectionTestUtils;

/** Project 도메인 테스트용 준비물. DB 없이 쓰므로 사례 id 는 직접 붙인다 */
final class ProjectFixture {

    static final List<String> SKILLS = List.of("Spring Boot", "Redis", "Kafka");
    private static final AtomicLong IDS = new AtomicLong(1000);

    private ProjectFixture() {
    }

    static ProjectDraft draft() {
        return new ProjectDraft("결제 승인 API 성능 개선", ProjectPeriod.of(YearMonth.of(2024, 5), YearMonth.of(2024, 9)),
                Affiliation.PERSONAL, null, "프로모션 기간 결제 승인 응답이 1.2초까지 느려졌다. Redis 캐시로 0.3초로 줄였다.",
                Set.of(1L, 2L), List.of("https://github.com/me/pay"));
    }

    static Project draftProject() {
        return withId(Project.createDraft(1L, draft()));
    }

    static EpisodeContent content(String title, Clarification... clarifications) {
        return new EpisodeContent(title, "백엔드", "응답 1.2초", "Redis 캐시", "0.3초", List.of("spring boot", "Redis"),
                List.of(new Metric("평균 응답시간", "1.2s", "0.3s", "raw_text")), List.of(new SourceSpan(0, 20)),
                List.of(clarifications));
    }

    static Clarification question(String key) {
        return new Clarification(key, "result", "응답 속도 개선 수치가 있나요?", "예: 1.2초 → 0.3초");
    }

    /** 사례 2개로 나눈 검토 중 프로젝트 */
    static Project reviewing(EpisodeContent... drafts) {
        Project project = draftProject();
        project.checkCanSplit();
        project.applySplit(drafts.length == 0 ? List.of(content("사례 A"), content("사례 B")) : List.of(drafts),
                SKILLS, new AiTrace("qwen", "p1-v1"), null);
        return assignIds(project);
    }

    static Project confirmedWithFirstEpisode() {
        Project project = reviewing();
        List<Episode> episodes = project.reviewEpisodes();
        project.confirmEpisode(episodes.get(0).getId());
        project.excludeEpisode(episodes.get(1).getId());
        project.confirm();
        return project;
    }

    /** JPA 가 붙여 주는 id 대신 테스트에서 붙인다 */
    static Project assignIds(Project project) {
        for (Episode episode : project.getEpisodes()) {
            if (episode.getId() == null) {
                ReflectionTestUtils.setField(episode, "id", IDS.incrementAndGet());
            }
        }
        return project;
    }

    private static Project withId(Project project) {
        ReflectionTestUtils.setField(project, "id", IDS.incrementAndGet());
        return project;
    }
}
