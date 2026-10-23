package com.nerdnam.common.error;

import java.util.Map;

/**
 * 도메인 규칙 위반이나 예상 가능한 실패를 나타내는 예외.
 * 예: throw new BusinessException(ErrorCode.DOMAIN_RULE_VIOLATED, "확인 필요 항목이 남은 사례는 확정할 수 없어요.");
 *
 * <p>{@code properties} 에 넣은 값은 오류 응답(ProblemDetail)에 그대로 실린다.
 * 예: 삭제 거부 시 어디서 쓰는지 → {@code Map.of("usages", usages)}
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> properties;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, Map.of());
    }

    public BusinessException(ErrorCode errorCode, String message, Map<String, Object> properties) {
        super(message);
        this.errorCode = errorCode;
        this.properties = Map.copyOf(properties);
    }

    public static BusinessException notFound() {
        return new BusinessException(ErrorCode.NOT_FOUND);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Map<String, Object> properties() {
        return properties;
    }
}
