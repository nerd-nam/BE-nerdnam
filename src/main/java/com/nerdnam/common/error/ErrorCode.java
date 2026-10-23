package com.nerdnam.common.error;

import org.springframework.http.HttpStatus;

/**
 * 웹에 내려주는 오류 코드. 웹은 code 로 분기하고, message 는 화면에 그대로 보여줄 수 있는 문장으로 쓴다.
 * 모듈별 규칙 위반은 여기에 계속 추가한다. (예: 사례 확정 조건, 확정된 프로젝트 수정)
 */
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값을 확인해 주세요."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없어요."),
    CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "서비스를 이용하려면 현재 약관에 동의해 주세요."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "대상을 찾을 수 없어요."),
    CONFLICT(HttpStatus.CONFLICT, "다른 곳에서 먼저 수정됐어요. 새로고침 후 다시 시도해 주세요."),
    IN_USE(HttpStatus.CONFLICT, "다른 곳에서 사용 중이라 삭제할 수 없어요."),
    AI_JOB_RUNNING(HttpStatus.CONFLICT, "이미 진행 중인 AI 작업이 있어요. 끝난 뒤 다시 시도해 주세요."),
    DOMAIN_RULE_VIOLATED(HttpStatus.UNPROCESSABLE_CONTENT, "지금은 할 수 없는 작업이에요."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "분석을 시작하지 못했어요. 입력한 내용은 그대로 남아 있으니 잠시 후 다시 시도해 주세요."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "일시적인 오류가 발생했어요.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
