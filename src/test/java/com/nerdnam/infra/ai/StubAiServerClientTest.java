package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class StubAiServerClientTest {

    private final StubAiServerClient stub = new StubAiServerClient();

    @Test
    void 같은_텍스트는_항상_같은_차원의_단위_벡터다() {
        float[] first = stub.embed(List.of(EmbedItem.of("결제 승인 API")), EmbedPurpose.DOCUMENT).vectors().getFirst();
        float[] second = stub.embed(List.of(EmbedItem.of("결제 승인 API")), EmbedPurpose.DOCUMENT).vectors().getFirst();

        assertThat(first).hasSize(StubAiServerClient.DIMENSIONS).containsExactly(second);
        double norm = 0;
        for (float v : first) {
            norm += v * v;
        }
        assertThat(norm).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void 용도가_다르면_다른_벡터다() {
        float[] document = stub.embed(List.of(EmbedItem.of("Kafka")), EmbedPurpose.DOCUMENT).vectors().getFirst();
        float[] query = stub.embed(List.of(EmbedItem.of("Kafka")), EmbedPurpose.QUERY).vectors().getFirst();

        assertThat(document).isNotEqualTo(query);
    }

    @Test
    void 스텁은_항상_준비_상태다() {
        assertThat(stub.health().ready()).isTrue();
    }

    /** 계약 v0.3.0: 줄마다 약어 확장어를 돌려준다. 없으면 빈 목록이다 (기획서 5.4) */
    @Test
    void 요건_추출은_약어의_확장어를_돌려준다_v0_3_0() {
        var request = new RequirementExtraction.Request(List.of(
                new RequirementExtraction.Line(1L, "required", "MSA 환경 개발 경험 3년 이상"),
                new RequirementExtraction.Line(2L, "required", "Redis 경험"),
                new RequirementExtraction.Line(3L, "preferred", "Kubernetes 운영 경험")));

        var lines = stub.extractRequirements(request).lines();

        assertThat(lines.get(0).expansions())
                .containsExactly("Microservice Architecture", "마이크로서비스 아키텍처");
        assertThat(lines.get(0).minYears()).isEqualTo(3);
        assertThat(lines.get(1).expansions()).as("약어가 없으면 빈 목록").isEmpty();
        assertThat(lines.get(2).expansions()).as("줄에 이미 있는 말은 확장어로 넣지 않는다").isEmpty();
    }

    @Test
    void 같은_입력이면_확장어도_항상_같은_순서다() {
        var request = new RequirementExtraction.Request(List.of(
                new RequirementExtraction.Line(1L, "required", "MSA 와 K8S 와 RDBMS 경험")));

        var first = stub.extractRequirements(request).lines().getFirst().expansions();
        var again = stub.extractRequirements(request).lines().getFirst().expansions();

        assertThat(first).as("줄마다 최대 3개").hasSize(3).isEqualTo(again);
    }

    /** 기획서 v1.20 입력 규칙 — 진짜 AI 서버는 422 를 주므로 스텁도 AI_ERROR 로 실패시킨다 */
    @Test
    void 입력_규칙을_어기면_AI_오류다_v1_20() {
        assertThatThrownBy(() -> stub.restructure(restructure("merge", "합쳐 줘"))).isInstanceOf(error());
        assertThatThrownBy(() -> stub.restructure(restructure(RestructureRequest.MODE_SPLIT, " ")))
                .as("다시 나누기에는 피드백이 필요하다")
                .isInstanceOf(error());
        assertThatThrownBy(() -> stub.embed(List.of(), EmbedPurpose.DOCUMENT)).isInstanceOf(error());
        assertThatThrownBy(() -> stub.gapGuide(new GapGuideCall.Request(
                List.of(new GapGuideCall.Requirement(1L, "required", "Kafka 운영", "met", List.of())), List.of())))
                .as("충족한 요건은 갭 가이드 대상이 아니다")
                .isInstanceOf(error());
        assertThatThrownBy(() -> stub.guide(new GuideCall.Request("motivation", "OO페이", "백엔드", "소개",
                List.of(), List.of(), null)))
                .as("탭 이름은 웹 주소와 같은 값(motive 등)만")
                .isInstanceOf(error());
    }

    @Test
    void 첫_나누기와_다시_나누기는_모두_사례를_만든다_v1_20() {
        String source = "응답이 1.2초까지 느려졌다. Redis 캐시를 붙여 0.3초가 됐다.\n\n부하 테스트 절차를 만들었다.";

        assertThat(stub.restructure(restructure(RestructureRequest.MODE_INITIAL, null, source)).episodes()).hasSize(2);
        assertThat(stub.restructure(restructure(RestructureRequest.MODE_SPLIT, "두 사례를 합쳐 줘", source)).episodes())
                .hasSize(1);
    }

    private static Class<AiServerUnavailableException> error() {
        return AiServerUnavailableException.class;
    }

    private static RestructureRequest restructure(String mode, String feedback) {
        return restructure(mode, feedback, "원문 한 문단.");
    }

    private static RestructureRequest restructure(String mode, String feedback, String sourceText) {
        return new RestructureRequest(mode, "결제 승인 API 성능 개선", sourceText, List.of("Redis"),
                List.of(), List.of(), null, feedback, null);
    }
}
