package com.nerdnam.profile.application;

/**
 * 기술스택·경력이 바뀌었다 (추가·수정·삭제). rag 가 그 회원의 기술·경력 청크를 다시 맞춘다 (F3: 저장 즉시 등록).
 * 바뀐 내용이 DB 에 반영(flush)된 뒤, 같은 트랜잭션 안에서 발행한다.
 */
public record ProfileChanged(Long memberId) {
}
