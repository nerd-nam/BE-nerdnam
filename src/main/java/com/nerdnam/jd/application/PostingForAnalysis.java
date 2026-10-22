package com.nerdnam.jd.application;

import java.util.List;

/**
 * 분석을 시작할 때 matching 이 받아 가는 공고 내용 (검사를 통과한 것).
 *
 * @param requirements 필수 → 우대 순. skills·minYears 는 추출 전이면 비어 있다
 */
public record PostingForAnalysis(Long postingId, String companyName, String positionName, String companyIntro,
                                 List<String> tasks, List<Line> requirements) {

    /** @param type REQUIRED / PREFERRED, order 1부터 */
    public record Line(Long requirementId, String type, String text, int order, List<String> skills, Integer minYears) {
    }
}
