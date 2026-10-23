package com.nerdnam.infra.ai;

/**
 * AI 서버 엔드포인트. 경로와 읽기 시간 제한 설정 키를 한곳에 모은다 (기획서 5.5 "AI 서버 공통 규칙").
 *
 * <p>읽기 시간 제한은 엔드포인트마다 다르다 (`nerdnam.ai.read-timeouts.*`). 설정 키는 이름을 소문자·하이픈으로
 * 바꾼 것이다 (`GAP_GUIDE` → `gap-guide`).
 */
public enum AiEndpoint {

    HEALTH("/health"),
    EMBED("/embed"),
    REQUIREMENTS_EXTRACT("/requirements/extract"),
    RESTRUCTURE("/restructure"),
    MATCH("/match"),
    GAP_GUIDE("/gap-guide"),
    GUIDE("/guide");

    private final String path;

    AiEndpoint(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    /** 로그·오류에 남기는 이름 (예: {@code requirements/extract}) */
    public String operation() {
        return path.substring(1);
    }
}
