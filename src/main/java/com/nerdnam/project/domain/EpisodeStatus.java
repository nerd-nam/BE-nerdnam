package com.nerdnam.project.domain;

/**
 * 사례 상태. SUPERSEDED·DISCARDED 는 화면에 보이지 않고 기록으로만 남는다.
 * <ul>
 *   <li>SUPERSEDED — 다시 나누기 또는 재등록 후 다시 확정으로 대체됨</li>
 *   <li>DISCARDED — 확정 전 검토 결과를 버림 (S04 진입·온보딩 밖으로, 기획서 v1.14)</li>
 * </ul>
 */
public enum EpisodeStatus {
    REVIEW,
    CONFIRMED,
    EXCLUDED,
    SUPERSEDED,
    DISCARDED;

    public boolean isDecided() {
        return this == CONFIRMED || this == EXCLUDED;
    }
}
