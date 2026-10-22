package com.nerdnam.matching.domain;

/**
 * 근거 카드 이름. 청크 텍스트는 "기술: Redis", "경력: OO커머스 · 팀 · 직무", "프로젝트: 제목", "사례: 제목" 줄로 시작한다.
 * 사례는 "프로젝트 제목 · 사례 제목".
 */
public final class EvidenceLabel {

    private EvidenceLabel() {
    }

    public static String of(EvidenceSourceType type, String content) {
        String project = value(content, "프로젝트: ");
        return switch (type) {
            case SKILL -> orElse(value(content, "기술: "), content);
            case CAREER -> {
                String career = value(content, "경력: ");
                yield career == null ? firstLine(content) : career.split(" · ")[0];
            }
            case PROJECT_SUMMARY -> orElse(project, content);
            case EPISODE -> {
                String episode = value(content, "사례: ");
                yield episode == null ? orElse(project, content) : (project == null ? episode : project + " · " + episode);
            }
        };
    }

    private static String value(String content, String prefix) {
        for (String line : content.split("\n")) {
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length()).strip();
            }
        }
        return null;
    }

    private static String orElse(String value, String content) {
        return value == null ? firstLine(content) : value;
    }

    private static String firstLine(String content) {
        int newline = content.indexOf('\n');
        return newline < 0 ? content : content.substring(0, newline);
    }
}
