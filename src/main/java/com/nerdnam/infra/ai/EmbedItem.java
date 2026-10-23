package com.nerdnam.infra.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 임베딩할 텍스트 한 개 (기획서 5.5 {@code POST /embed}의 {@code items[]}).
 *
 * @param text  임베딩할 텍스트. 접두어는 AI 서버가 붙이므로 원래 텍스트만 보낸다
 * @param title 청크 제목 (선택). 넣으면 검색 품질이 좋아진다. 요건 검색(query)에는 쓰지 않으므로 {@link #of}로 만든다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbedItem(String text, String title) {

    /** 제목 없이 (요건 검색 등) */
    public static EmbedItem of(String text) {
        return new EmbedItem(text, null);
    }
}
