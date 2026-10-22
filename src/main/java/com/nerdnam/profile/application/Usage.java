package com.nerdnam.profile.application;

/** 삭제를 막은 사용처 (ADR-0002 Q7·Q8). 오류 응답의 usages[] 로 나간다 */
public record Usage(Type type, Long id, String name) {

    public enum Type { CAREER, PROJECT }
}
