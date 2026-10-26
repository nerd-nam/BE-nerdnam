package com.nerdnam.guide.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface WritingGuideRepository extends Repository<WritingGuide, Long> {

    WritingGuide saveAndFlush(WritingGuide guide);

    Optional<WritingGuide> findById(Long id);

    @Query("select coalesce(max(g.generationNo), 0) from WritingGuide g where g.fitAnalysisId = :a and g.tab = :tab")
    int maxGenerationNo(@Param("a") Long fitAnalysisId, @Param("tab") GuideTab tab);

    Optional<WritingGuide> findFirstByFitAnalysisIdAndTabOrderByGenerationNoDesc(Long fitAnalysisId, GuideTab tab);

    /** 이 분석·이 탭에 완료된 결과가 있나 — 다시 만들기의 피드백 필수 판단에 쓴다 (BE #23) */
    @Query("""
            select count(g) > 0 from WritingGuide g
            where g.fitAnalysisId = :a and g.tab = :tab and g.status = com.nerdnam.guide.domain.GuideStatus.COMPLETED
            """)
    boolean hasCompleted(@Param("a") Long fitAnalysisId, @Param("tab") GuideTab tab);

    /** 탭마다 가장 최근 생성 */
    @Query("""
            select g from WritingGuide g where g.fitAnalysisId = :a and g.generationNo =
              (select max(g2.generationNo) from WritingGuide g2 where g2.fitAnalysisId = g.fitAnalysisId and g2.tab = g.tab)
            """)
    List<WritingGuide> findLatestPerTab(@Param("a") Long fitAnalysisId);
}
