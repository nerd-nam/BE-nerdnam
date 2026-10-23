package com.nerdnam.jd.domain;

/**
 * "적합도 분석하기"를 눌렀을 때의 입력 오류 하나 (기획서 S07 메시지 표). 웹은 field·index 위치에 message 를 띄운다.
 *
 * @param field companyName / positionName / required / preferred / tasks
 * @param index 목록 안의 줄 위치 (0부터, 빈 줄을 뺀 저장된 줄 기준). 목록 전체·단일 칸 오류면 null
 */
public record PostingViolation(Code code, String field, Integer index, String message) {

    public enum Code {
        COMPANY_NAME_REQUIRED("회사명을 입력해 주세요."),
        POSITION_REQUIRED("포지션을 입력해 주세요."),
        REQUIRED_REQUIREMENT_MISSING("필수사항을 1개 이상 입력해 주세요."),
        LINE_TOO_LONG("한 줄이 너무 길어요. 요건이 여러 개라면 줄을 나눠 주세요. (150자 이하)"),
        DUPLICATE_LINE("같은 내용이 이미 있어요.");

        private final String message;

        Code(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }

    static PostingViolation of(Code code, String field, Integer index) {
        return new PostingViolation(code, field, index, code.message());
    }
}
