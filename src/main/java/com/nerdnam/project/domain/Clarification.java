package com.nerdnam.project.domain;

import org.jspecify.annotations.Nullable;

/**
 * 확인 필요 질문 (기획서의 unverified). AI 가 원문에서 찾지 못해 사용자에게 묻는다.
 *
 * @param key      사례 안에서 질문을 가리키는 값 (답하기·빼고 진행할 때 쓴다)
 * @param field    어느 칸에 관한 질문인지 (problem, solution, result, metrics 등)
 * @param question 화면에 보여줄 질문
 * @param example  입력 예시 (없을 수 있음)
 */
public record Clarification(String key, @Nullable String field, String question, @Nullable String example) {
}
