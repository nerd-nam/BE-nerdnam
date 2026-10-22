package com.nerdnam.member.application;

/** 탈퇴. DB 데이터는 CASCADE 로 이미 지워졌다. 받는 쪽은 캐시·세션처럼 DB 밖에 남은 것을 지운다 */
public record MemberWithdrawn(Long memberId) {
}
