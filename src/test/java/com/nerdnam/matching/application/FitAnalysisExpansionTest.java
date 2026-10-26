package com.nerdnam.matching.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nerdnam.infra.ai.AiServerClient;
import com.nerdnam.infra.ai.EmbedItem;
import com.nerdnam.infra.ai.EmbedPurpose;
import com.nerdnam.infra.ai.Embeddings;
import com.nerdnam.infra.ai.GapGuideCall;
import com.nerdnam.infra.ai.MatchCall;
import com.nerdnam.infra.ai.RequirementExtraction;
import com.nerdnam.jd.application.JobPostingService;
import com.nerdnam.jd.application.PostingForAnalysis;
import com.nerdnam.profile.application.CareerService;
import com.nerdnam.profile.application.UserSkillService;
import com.nerdnam.rag.application.ChunkSearch;
import com.nerdnam.skill.application.SkillCatalog;
import com.nerdnam.skill.application.SkillSummary;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 확장어가 추출 결과에서 실제 검색까지 연결되고, 원문·판정 입력에는 섞이지 않는지 확인한다. */
class FitAnalysisExpansionTest {

    @Test
    void 추출_응답_순서가_달라도_요건_id로_확장하고_판정에는_원문을_보낸다() {
        AiServerClient ai = mock(AiServerClient.class);
        JobPostingService postings = mock(JobPostingService.class);
        ChunkSearch search = mock(ChunkSearch.class);
        CareerService careers = mock(CareerService.class);
        UserSkillService skills = mock(UserSkillService.class);
        SkillCatalog catalog = mock(SkillCatalog.class);
        FitAnalysisPipeline pipeline = new FitAnalysisPipeline(ai, postings, search, careers, skills, catalog);
        PostingForAnalysis posting = new PostingForAnalysis(10L, "회사", "개발자", "", List.of(), List.of(
                new PostingForAnalysis.Line(1L, "REQUIRED", "Spring 경험", 1, List.of("Spring"), null),
                new PostingForAnalysis.Line(2L, "PREFERRED", "Redis 경험", 2, List.of("Redis"), null)));
        // AI가 줄을 거꾸로 돌려줘도 확장어는 같은 id의 요건에만 붙어야 한다.
        when(ai.extractRequirements(any())).thenReturn(new RequirementExtraction.Result(List.of(
                new RequirementExtraction.Extracted(2L, List.of("Redis"), List.of(), null),
                new RequirementExtraction.Extracted(1L, List.of("Spring"), List.of("Spring Framework"), null)),
                "모델", "p2_v2"));
        when(postings.applyExtraction(eq(7L), eq(10L), any())).thenReturn(posting);
        when(catalog.find("Spring Framework"))
                .thenReturn(Optional.of(new SkillSummary(100L, "Spring", null, true)));
        float[] springVector = {1f, 0f};
        float[] redisVector = {0f, 1f};
        when(ai.embed(any(), eq(EmbedPurpose.QUERY)))
                .thenReturn(new Embeddings(List.of(springVector, redisVector), "모델"));
        when(ai.match(any())).thenReturn(new MatchCall.Result(List.of(
                new MatchCall.Judgement(1L, "unmet", List.of(), "근거 없음"),
                new MatchCall.Judgement(2L, "unmet", List.of(), "근거 없음")), "모델", "p3_v3"));
        when(ai.gapGuide(any())).thenReturn(new GapGuideCall.Result(List.of(), "모델", "p6_v1"));

        FitAnalysisPipeline.Output result = pipeline.run(7L, posting);

        verify(postings).applyExtraction(7L, 10L, Map.of(
                1L, new JobPostingService.Extracted(List.of("Spring"), null),
                2L, new JobPostingService.Extracted(List.of("Redis"), null)));
        verify(ai).embed(List.of(EmbedItem.of("Spring 경험 (Spring Framework)"), EmbedItem.of("Redis 경험")),
                EmbedPurpose.QUERY);
        verify(search).search(7L, springVector, List.of("Spring"), 20);
        verify(search).search(7L, redisVector, List.of("Redis"), 20);
        verify(ai).match(new MatchCall.Request(List.of(
                new MatchCall.Requirement(1L, "required", "Spring 경험", List.of("Spring"), null, List.of()),
                new MatchCall.Requirement(2L, "preferred", "Redis 경험", List.of("Redis"), null, List.of()))));
        assertThat(result.judged()).extracting(j -> j.requirement().text())
                .containsExactly("Spring 경험", "Redis 경험");
    }
}
