package com.nerdnam.matching.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.matching.domain.RequirementMatch.Judged;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 적합도 리포트 충족 수·한 줄 진단 문장 틀 (ADR-0003) */
class FitReportTest {

    private static final Candidate PAY_EPISODE = new Candidate(11, EvidenceSourceType.EPISODE, 201, 2,
            "프로젝트: 결제 승인 API 성능 개선\n사례: 결제 승인 응답 지연 개선\n문제: ...", 0.9);
    private static final Candidate REDIS = new Candidate(12, EvidenceSourceType.SKILL, 5, 1,
            "기술: Redis\n카테고리: DB", 0.5);

    private int order = 0;

    private Judged judged(String type, String text, Verdict llm, Candidate... evidence) {
        return new Judged(new RequirementSnapshot((long) ++order, type, text, order, List.of(), null), null, llm,
                "이유", List.of(evidence), null);
    }

    private FitReport report(Judged... judged) {
        FitAnalysis analysis = FitAnalysis.start(1L, 1L);
        analysis.complete(new ArrayList<>(List.of(judged)), "m", "p");
        return analysis.report();
    }

    @Test
    void 필수를_모두_충족하고_한_경험이_여러_요건을_뒷받침하면() {
        FitReport report = report(
                judged("REQUIRED", "대용량 트래픽 처리 경험", Verdict.MET, PAY_EPISODE, REDIS),
                judged("REQUIRED", "RDBMS 설계·쿼리 튜닝 경험", Verdict.MET, PAY_EPISODE),
                judged("PREFERRED", "Kafka 등 메시지 큐 운영", Verdict.MET, PAY_EPISODE),
                judged("PREFERRED", "Kubernetes 운영 경험", Verdict.UNMET));

        assertThat(report.diagnosis()).isEqualTo(
                "필수 2개 모두 근거 경험이 있어요. 결제 승인 API 성능 개선 · 결제 승인 응답 지연 개선 경험이 가장 많은 요건(3개)을 뒷받침해요.");
        assertThat(report.required()).isEqualTo(new FitReport.Count(2, 0, 0, 2));
        assertThat(report.preferred()).isEqualTo(new FitReport.Count(1, 0, 1, 2));
    }

    @Test
    void 필수_미충족이_있으면_그_수와_첫_요건_이름() {
        FitReport report = report(
                judged("REQUIRED", "Java/Spring 기반 개발 3년 이상", Verdict.MET, REDIS),
                judged("REQUIRED", "결제·금융 도메인 경험이 있는 분", Verdict.UNMET),
                judged("REQUIRED", "Kubernetes 운영", Verdict.UNMET));

        assertThat(report.diagnosis()).isEqualTo("필수 3개 중 2개는 근거 경험을 찾지 못했어요(결제·금융 도메인 경험이 있는 분 등).");
    }

    @Test
    void 필수_부분_충족만_있으면() {
        FitReport report = report(
                judged("REQUIRED", "결제·금융 도메인 경험", Verdict.PARTIAL, PAY_EPISODE),
                judged("REQUIRED", "RDBMS", Verdict.MET, REDIS));

        assertThat(report.diagnosis()).isEqualTo("필수 2개 중 1개는 부분 충족이에요.");
        assertThat(report.required()).isEqualTo(new FitReport.Count(1, 1, 0, 2));
    }

    @Test
    void 같은_요건_안의_같은_원본은_한_번만_센다() {
        Candidate sameEpisodeOtherChunk = new Candidate(99, EvidenceSourceType.EPISODE, 201, 1, PAY_EPISODE.content(), 0.1);
        FitReport report = report(judged("REQUIRED", "트래픽", Verdict.MET, PAY_EPISODE, sameEpisodeOtherChunk));

        assertThat(report.diagnosis()).isEqualTo("필수 1개 모두 근거 경험이 있어요.");
    }

    @Test
    void 충족이_아닌_요건에는_갭_가이드가_붙고_AI가_없으면_기본_문구() {
        FitAnalysis analysis = FitAnalysis.start(1L, 1L);
        analysis.complete(List.of(
                judged("REQUIRED", "A", Verdict.MET, REDIS),
                judged("REQUIRED", "B", Verdict.PARTIAL, REDIS),
                judged("PREFERRED", "C", Verdict.UNMET).withGapGuide(
                        new GapGuide("AI 가이드", "AI 하지 말 것", List.of(12L), GapGuide.GapSource.AI))), "m", "p");

        List<RequirementMatch> matches = analysis.getMatches();
        assertThat(matches.get(0).gapGuide()).isNull();
        assertThat(matches.get(1).gapGuide()).isEqualTo(GapGuide.fallbackFor(Verdict.PARTIAL));
        assertThat(matches.get(1).getGapAdvice()).isEqualTo("경험한 범위를 정확히 밝혀 쓰세요.");
        assertThat(matches.get(2).getGapSource()).isEqualTo(GapGuide.GapSource.AI);
    }

    @Test
    void 연차_규칙이_더_낮으면_최종_판정이_낮아진다() {
        Judged judged = new Judged(new RequirementSnapshot(1L, "REQUIRED", "Java 3년 이상", 1, List.of("Java"), 3),
                Verdict.PARTIAL, Verdict.MET, "이유", List.of(), null);

        assertThat(judged.finalVerdict()).isEqualTo(Verdict.PARTIAL);
    }

    @Test
    void 근거_이름은_청크_텍스트에서() {
        assertThat(EvidenceLabel.of(EvidenceSourceType.CAREER, "경력: OO커머스 · 결제팀 · 백엔드\n기간: ...")).isEqualTo("OO커머스");
        assertThat(EvidenceLabel.of(EvidenceSourceType.SKILL, REDIS.content())).isEqualTo("Redis");
        assertThat(EvidenceLabel.of(EvidenceSourceType.PROJECT_SUMMARY, "프로젝트: 결제\n기간: ...")).isEqualTo("결제");
    }
}
