package com.nerdnam.infra.ai;

/** 임베딩 용도. 문서(청크 저장)와 질의(요건 검색)는 AI 서버가 다른 접두어를 붙인다 (ADR-0001) */
public enum EmbedPurpose {
    DOCUMENT,
    QUERY
}
