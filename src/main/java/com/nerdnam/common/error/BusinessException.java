package com.nerdnam.common.error;

/**
 * 도메인 규칙 위반이나 예상 가능한 실패를 나타내는 예외.
 * 예: throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확인 필요 항목이 남은 사례는 확정할 수 없어요.");
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
