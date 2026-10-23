package com.nerdnam.project.domain;

/** 정량 성과 하나 (기획서 4.3 metrics). source = 근거 위치 (예: raw_text, supplement) */
public record Metric(String name, String before, String after, String source) {
}
