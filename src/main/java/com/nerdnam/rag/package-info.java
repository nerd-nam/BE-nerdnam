/**
 * RAG 등록·검색 (F3). 청크 생성, 벡터 저장, user_id 필터 하이브리드 검색.
 * 도메인 규칙이 적은 기술 모듈이라 JPA 대신 JdbcClient + SQL 로 구현한다. 검색 SQL은 이 모듈에만 둔다.
 * 테이블: rag.chunk
 */
package com.nerdnam.rag;
