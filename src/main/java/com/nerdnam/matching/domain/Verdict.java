package com.nerdnam.matching.domain;

import java.util.Locale;

/** 판정. 순서: 미충족 < 부분 충족 < 충족 (D-5: 두 판정이 다르면 낮은 쪽) */
public enum Verdict {
    UNMET,
    PARTIAL,
    MET;

    /** AI 응답(met / partial / unmet)을 읽는다. 모르는 값은 미충족 — 근거 없이 충족으로 보지 않는다 */
    public static Verdict fromAi(String value) {
        if (value == null) {
            return UNMET;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "met" -> MET;
            case "partial" -> PARTIAL;
            default -> UNMET;
        };
    }

    public static Verdict lower(Verdict a, Verdict b) {
        return a.ordinal() <= b.ordinal() ? a : b;
    }
}
