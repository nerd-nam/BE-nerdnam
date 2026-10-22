package com.nerdnam.matching.domain;

import java.util.List;

/**
 * 갭 대응 가이드 (ADR-0003) — 미충족·부분 충족 요건에만. AI(/gap-guide)가 만들고, 실패하면 판정별 기본 문구.
 *
 * @param citedChunkIds 가이드가 인용한 인접 경험 청크 (이 회원·입력 후보 안의 것만)
 */
public record GapGuide(String advice, String avoid, List<Long> citedChunkIds, GapSource source) {

    public enum GapSource {
        AI,
        FALLBACK
    }

    public GapGuide {
        citedChunkIds = citedChunkIds == null ? List.of() : List.copyOf(citedChunkIds);
    }

    /** AI 가 실패했을 때(GPU 꺼짐 포함)의 기본 문구 (ADR-0003) */
    public static GapGuide fallbackFor(Verdict verdict) {
        return switch (verdict) {
            case UNMET -> new GapGuide("학습 중이거나 개인 프로젝트가 있다면 '학습 중'으로 따로 적으세요.", "사용해 본 것처럼 쓰기",
                    List.of(), GapSource.FALLBACK);
            case PARTIAL -> new GapGuide("경험한 범위를 정확히 밝혀 쓰세요.", "경험 범위를 부풀리기", List.of(), GapSource.FALLBACK);
            case MET -> throw new IllegalArgumentException("충족한 요건에는 갭 대응 가이드가 없어요.");
        };
    }
}
