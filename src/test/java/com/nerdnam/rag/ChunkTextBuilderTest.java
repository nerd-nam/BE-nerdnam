package com.nerdnam.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.rag.application.ChunkDraft;
import com.nerdnam.rag.application.ChunkSources.CareerSource;
import com.nerdnam.rag.application.ChunkSources.EpisodeSource;
import com.nerdnam.rag.application.ChunkSources.ProjectSource;
import com.nerdnam.rag.application.ChunkSources.SkillSource;
import com.nerdnam.rag.application.ChunkTextBuilder;
import com.nerdnam.rag.application.ChunkType;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 청크 텍스트 모양 (기획서 4.4) — 임베딩 품질이 여기에 달려 있어 문장 틀을 고정한다 */
class ChunkTextBuilderTest {

    private static final EpisodeSource EPISODE = new EpisodeSource(201, 2, "결제 승인 응답 지연 개선", "백엔드 리드 (팀 4명)",
            "프로모션 기간 결제 승인 응답이 평균 1.2초까지 느려져 이탈 발생",
            "가맹점 조회를 Redis 캐시로 전환, 승인 후처리를 Kafka 비동기로 분리",
            "평균 응답 1.2초 → 0.3초, 피크 TPS 약 2배 처리", List.of("Spring Boot", "Redis", "Kafka"), true);
    private static final ProjectSource PROJECT = new ProjectSource(102, 7, "결제 승인 API 성능 개선",
            YearMonth.of(2024, 5), YearMonth.of(2024, 9), List.of("Kafka", "Redis", "Spring Boot"), List.of(EPISODE));

    @Test
    void 기술_청크는_이름_카테고리_숙련도_기준_사용처() {
        ChunkDraft chunk = ChunkTextBuilder.skill(new SkillSource(5, "Spring Boot", "FRAMEWORK", 3,
                List.of("OO커머스 경력", "결제 승인 API 성능 개선 프로젝트")));

        assertThat(chunk.content()).isEqualTo("""
                기술: Spring Boot
                카테고리: 프레임워크
                숙련도: 능숙 — 설계부터 문제 해결까지 혼자 할 수 있다
                사용처: OO커머스 경력, 결제 승인 API 성능 개선 프로젝트""");
        assertThat(chunk.type()).isEqualTo(ChunkType.SKILL);
        assertThat(chunk.skills()).containsExactly("Spring Boot");
    }

    @Test
    void 경력_청크는_재직_중이면_기준_월까지_개월_수를_센다() {
        ChunkDraft chunk = ChunkTextBuilder.career(new CareerSource(1, 3, "OO커머스", "결제플랫폼팀", "백엔드 개발자", "선임",
                "FULL_TIME", YearMonth.of(2023, 3), null, List.of("결제 API 설계·운영", "정산 배치 개선"),
                List.of("Kafka", "Spring Boot")), YearMonth.of(2025, 2));

        assertThat(chunk.content()).isEqualTo("""
                경력: OO커머스 · 결제플랫폼팀 · 백엔드 개발자 (선임, 정규직)
                기간: 2023-03 ~ 재직 중 (24개월)
                주요 업무:
                - 결제 API 설계·운영
                - 정산 배치 개선
                사용 기술: Kafka, Spring Boot""");
        assertThat(chunk.months()).isEqualTo(24);
        assertThat(chunk.periodEnd()).isNull();
        assertThat(chunk.sourceVersion()).isEqualTo(3);
    }

    @Test
    void 프로젝트_요약_청크는_확정한_사례마다_한_줄() {
        ChunkDraft chunk = ChunkTextBuilder.projectSummary(PROJECT);

        assertThat(chunk.content()).isEqualTo("""
                프로젝트: 결제 승인 API 성능 개선
                기간: 2024-05 ~ 2024-09
                역할: 백엔드 리드 (팀 4명)
                사용 기술: Kafka, Redis, Spring Boot
                사례:
                - 결제 승인 응답 지연 개선: 프로모션 기간 결제 승인 응답이 평균 1.2초까지 느려져 이탈 발생 → 가맹점 조회를 Redis 캐시로 전환, 승인 후처리를 Kafka 비동기로 분리 → 평균 응답 1.2초 → 0.3초, 피크 TPS 약 2배 처리""");
        assertThat(chunk.projectId()).isEqualTo(102L);
        assertThat(chunk.months()).isEqualTo(5);
        assertThat(chunk.hasMetric()).isTrue();
    }

    @Test
    void 사례_청크는_프로젝트_제목과_문제_해결_결과() {
        ChunkDraft chunk = ChunkTextBuilder.episode(PROJECT, EPISODE);

        assertThat(chunk.content()).isEqualTo("""
                프로젝트: 결제 승인 API 성능 개선
                사례: 결제 승인 응답 지연 개선
                역할: 백엔드 리드 (팀 4명)
                문제: 프로모션 기간 결제 승인 응답이 평균 1.2초까지 느려져 이탈 발생
                해결: 가맹점 조회를 Redis 캐시로 전환, 승인 후처리를 Kafka 비동기로 분리
                결과: 평균 응답 1.2초 → 0.3초, 피크 TPS 약 2배 처리
                사용 기술: Spring Boot, Redis, Kafka""");
        assertThat(chunk.sourceId()).isEqualTo(201L);
        assertThat(chunk.sourceVersion()).isEqualTo(2);
        assertThat(chunk.projectId()).isEqualTo(102L);
    }

    @Test
    void 기간이_없는_프로젝트는_기간_줄이_없다() {
        ProjectSource noPeriod = new ProjectSource(1, 0, "제목", null, null, List.of(), List.of());

        assertThat(ChunkTextBuilder.projectSummary(noPeriod).content()).isEqualTo("프로젝트: 제목\n사례:");
        assertThat(ChunkTextBuilder.projectSummary(noPeriod).months()).isNull();
    }
}
