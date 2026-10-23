package com.nerdnam.guide.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.Arrays;

/**
 * 작성 방향 탭 (S09). path = 웹 주소의 탭 이름(/apply/[jdId]/guide/[tab]).
 * AI 계약의 탭 이름도 같은 값이다 (기획서 v1.20 입력 규칙 — 다른 값이면 422).
 */
public enum GuideTab {
    MOTIVATION("motive"),
    SELF_INTRODUCTION("intro"),
    PORTFOLIO("portfolio"),
    CAREER_DESCRIPTION("career"),
    IMAGE_PROMPT("image");

    private final String path;

    GuideTab(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    /** AI 서버 {@code /guide} 의 tab 값. 웹 주소와 같은 이름을 쓴다 (기획서 v1.20) */
    public String aiName() {
        return path;
    }

    /** 웹 주소 이름(motive)과 enum 이름(MOTIVATION) 둘 다 받는다 */
    public static GuideTab parse(String value) {
        return Arrays.stream(values())
                .filter(t -> t.path.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "없는 탭이에요."));
    }
}
