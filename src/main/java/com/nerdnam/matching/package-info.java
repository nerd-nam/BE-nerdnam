/**
 * 적합도 분석·리포트 (F5 앞부분, 기획서 5.2의 application). FitAnalysis 애그리거트.
 * 요건 정보 추출 → 정규화 → 검색 → 규칙 판정 → AI 판정 → 결합·저장 흐름을 조율한다.
 * 'application'은 DDD 계층 이름과 겹쳐 혼동되므로 matching 으로 부른다.
 * 테이블: app.fit_analysis, app.requirement_match, app.match_evidence
 */
package com.nerdnam.matching;
