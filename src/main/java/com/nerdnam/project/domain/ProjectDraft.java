package com.nerdnam.project.domain;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * S04 입력 칸 전체 (임시 저장 단위). 임시 저장이라 제목·본문은 비어 있어도 된다.
 * 빈 링크 줄은 버린다.
 */
public record ProjectDraft(
        String title,
        ProjectPeriod period,
        Affiliation affiliation,
        Long careerId,
        String rawText,
        Set<Long> userSkillIds,
        List<String> links) {

    public static final int TITLE_MAX = 100;
    public static final int RAW_TEXT_MAX = 20_000;
    public static final int LINKS_MAX = 10;
    public static final int LINK_MAX = 1000;

    public ProjectDraft {
        title = Objects.requireNonNullElse(title, "").strip();
        if (title.length() > TITLE_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 " + TITLE_MAX + "자 이하로 입력해 주세요.");
        }
        period = period == null ? ProjectPeriod.empty() : period;
        affiliation = affiliation == null ? Affiliation.PERSONAL : affiliation;
        if (affiliation == Affiliation.CAREER && careerId == null) {   // P8
            throw new BusinessException(ErrorCode.INVALID_INPUT, "연결할 경력을 선택해 주세요.");
        }
        if (affiliation != Affiliation.CAREER) {
            careerId = null;
        }
        rawText = Objects.requireNonNullElse(rawText, "");
        if (rawText.length() > RAW_TEXT_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "본문은 " + RAW_TEXT_MAX + "자 이하로 입력해 주세요.");
        }
        userSkillIds = userSkillIds == null ? Set.of() : Set.copyOf(userSkillIds);
        links = links == null ? List.of() : links.stream().filter(Objects::nonNull).map(String::strip)
                .filter(l -> !l.isEmpty()).toList();
        if (links.size() > LINKS_MAX) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "링크는 " + LINKS_MAX + "개까지 넣을 수 있어요.");
        }
        if (links.stream().anyMatch(l -> l.length() > LINK_MAX)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "링크가 너무 길어요.");
        }
    }

    public static ProjectDraft empty() {
        return new ProjectDraft("", null, Affiliation.PERSONAL, null, "", Set.of(), List.of());
    }
}
