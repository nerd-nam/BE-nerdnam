package com.nerdnam.jd.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.List;
import java.util.Objects;

/**
 * S07 양식 전체 (임시 저장 단위). 임시 저장은 규칙을 검사하지 않으므로 비어 있거나 150자를 넘어도 된다.
 * 빈 줄만 버린다 (F4). DB 칸 크기를 넘는 값만 막는다.
 */
public record PostingContent(String companyName, String positionName, String postingUrl, String companyIntro,
                             List<String> required, List<String> preferred, List<String> tasks) {

    static final int HARD_LINE_MAX = 1000;

    public PostingContent {
        companyName = cut(companyName, 200, "회사명");
        positionName = cut(positionName, 200, "포지션");
        postingUrl = postingUrl == null || postingUrl.isBlank() ? null : cut(postingUrl, 1000, "공고 링크");
        companyIntro = Objects.requireNonNullElse(companyIntro, "");
        if (companyIntro.length() > 10_000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "회사 소개는 10000자 이하로 입력해 주세요.");
        }
        required = lines(required);
        preferred = lines(preferred);
        tasks = lines(tasks);
    }

    public static PostingContent empty() {
        return new PostingContent("", "", null, "", List.of(), List.of(), List.of());
    }

    private static List<String> lines(List<String> lines) {
        if (lines == null) {
            return List.of();
        }
        List<String> kept = lines.stream().filter(Objects::nonNull).map(String::strip).filter(l -> !l.isEmpty()).toList();
        if (kept.stream().anyMatch(l -> l.length() > HARD_LINE_MAX)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 줄이 너무 길어요. 요건이 여러 개라면 줄을 나눠 주세요. (150자 이하)");
        }
        if (kept.size() > 50) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "한 목록에는 50줄까지 넣을 수 있어요.");
        }
        return kept;
    }

    private static String cut(String value, int max, String name) {
        String v = Objects.requireNonNullElse(value, "").strip();
        if (v.length() > max) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, name + "은(는) " + max + "자 이하로 입력해 주세요.");
        }
        return v;
    }
}
