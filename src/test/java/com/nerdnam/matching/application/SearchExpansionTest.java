package com.nerdnam.matching.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.TestcontainersConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 요건 약어 확장어를 검색에 쓰는 규칙 (기획서 5.4 "요건 정규화", 계약 v0.3.0 {@code expansions}).
 * 확장어는 AI 가 주고({@code /requirements/extract}), API 가 검색 질의와 키워드 검색에 쓴다.
 */
@SpringBootTest(properties = "nerdnam.ai-job.sync=true")
@Import(TestcontainersConfiguration.class)
class SearchExpansionTest {

    @Autowired
    FitAnalysisPipeline pipeline;

    @Autowired
    JdbcClient jdbc;

    @Test
    void 검색_질의는_원문에_확장어를_덧붙인다() {
        assertThat(FitAnalysisPipeline.searchQuery("MSA 환경 개발 경험",
                List.of("Microservice Architecture", "마이크로서비스 아키텍처")))
                .isEqualTo("MSA 환경 개발 경험 (Microservice Architecture, 마이크로서비스 아키텍처)");
    }

    @Test
    void 확장어가_없으면_질의는_원문_그대로다() {
        assertThat(FitAnalysisPipeline.searchQuery("Redis 경험", List.of())).isEqualTo("Redis 경험");
        assertThat(FitAnalysisPipeline.searchQuery("Redis 경험", null)).isEqualTo("Redis 경험");
    }

    @Test
    void 키워드_검색에는_사전에_있는_확장어만_표준_이름으로_더한다() {
        // "Spring Framework" 는 사전에서 Spring 의 별칭이다. 뒤의 문장은 기술명이 아니라 사전에 없다
        List<String> keywords = pipeline.searchKeywords(List.of("Redis"),
                List.of("Spring Framework", "관계형 데이터베이스 관리 시스템"));

        assertThat(keywords).containsExactly("Redis", "Spring");
    }

    @Test
    void 확장어는_사전_보강_기록에_넣지_않는다() {
        String phrase = "관계형 데이터베이스 관리 시스템";

        pipeline.searchKeywords(List.of(), List.of(phrase));

        Integer logged = jdbc.sql("SELECT count(*) FROM app.unregistered_skill_name WHERE raw_name = :n")
                .param("n", phrase).query(Integer.class).single();
        assertThat(logged).as("확장어는 기술명이 아닐 수 있어 사전 보강 대상이 아니다").isZero();
    }

    @Test
    void 확장어가_없으면_요건_기술명만_쓴다() {
        assertThat(pipeline.searchKeywords(List.of("Redis"), List.of())).containsExactly("Redis");
        assertThat(pipeline.searchKeywords(List.of("Redis"), null)).containsExactly("Redis");
    }
}
