package com.nerdnam.matching.domain;

/**
 * 규칙 판정(연차)과 LLM 판정을 합친다 — 다르면 낮은 쪽 (D-5, ADR-0002).
 * 규칙 판정이 없으면(연차 조건이 없는 요건) LLM 판정 그대로.
 */
public final class VerdictPolicy {

    private VerdictPolicy() {
    }

    public static Verdict combine(Verdict rule, Verdict llm) {
        return rule == null ? llm : Verdict.lower(rule, llm);
    }
}
