package com.nerdnam.guide.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.Arrays;
import java.util.Locale;

/**
 * 작성 방향 탭 (S09). path = 웹 주소의 탭 이름(/apply/[jdId]/guide/[tab]), aiName = AI 계약의 탭 이름.
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

    public String aiName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 웹 주소 이름(motive)과 enum 이름(MOTIVATION) 둘 다 받는다 */
    public static GuideTab parse(String value) {
        return Arrays.stream(values())
                .filter(t -> t.path.equalsIgnoreCase(value) || t.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "없는 탭이에요."));
    }
}
