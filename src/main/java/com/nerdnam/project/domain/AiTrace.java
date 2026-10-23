package com.nerdnam.project.domain;

/** AI 결과를 만든 모델과 프롬프트 버전 (기획서 5.5: 모든 응답에 담아 결과와 함께 저장) */
public record AiTrace(String aiModel, String promptVersion) {

    public static AiTrace none() {
        return new AiTrace(null, null);
    }
}
