package com.nerdnam.matching.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface FitAnalysisRepository extends Repository<FitAnalysis, Long> {

    FitAnalysis saveAndFlush(FitAnalysis analysis);

    Optional<FitAnalysis> findById(Long id);

    Optional<FitAnalysis> findByIdAndMemberId(Long id, Long memberId);

    /** 화면은 가장 최근 분석을 본다 */
    Optional<FitAnalysis> findFirstByJobPostingIdAndMemberIdOrderByRequestedAtDescIdDesc(Long jobPostingId, Long memberId);

    /** 분석 기록 (S08 "이전 분석 보기", 기획서 v1.25) — 최신순 */
    List<FitAnalysis> findAllByJobPostingIdAndMemberIdOrderByRequestedAtDescIdDesc(Long jobPostingId, Long memberId);
}
