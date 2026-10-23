package com.nerdnam.project.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;

/**
 * 원문에서 사례의 근거가 된 구간 [start, end) — 문자 위치. 기준 텍스트는 {@link Project#sourceText()} (원문 + 보충 답변).
 * JSON 으로는 기획서 4.3 처럼 [0, 182] 배열로 저장한다.
 */
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public record SourceSpan(int start, int end) {

    @JsonCreator
    public SourceSpan {
        if (start < 0 || end < start) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "원문 구간이 올바르지 않아요.");
        }
    }
}
