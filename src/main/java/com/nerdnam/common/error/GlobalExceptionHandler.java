package com.nerdnam.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 컨트롤러의 예외를 RFC 9457 ProblemDetail 형식으로 바꿔 응답한다.
 * 응답 예: {"type":"about:blank","title":"DOMAIN_RULE_VIOLATED","status":422,"detail":"..."}
 *
 * <p>{@link ResponseEntityExceptionHandler}를 상속해 Spring MVC 의 기본 오류(404 없는 주소, 405 메서드,
 * 400 잘못된 JSON 등)는 원래 상태 코드 그대로 ProblemDetail 로 바꾼다. 상속하지 않으면 아래의
 * {@code Exception} 처리기가 이것들까지 500 으로 바꿔 버린다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusiness(BusinessException e) {
        ProblemDetail problem = problem(e.errorCode(), e.getMessage());
        e.properties().forEach(problem::setProperty);
        return ResponseEntity.status(e.errorCode().status()).body(problem);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(problem(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage()));
    }

    /** 요청 본문 검증(@Valid) 실패 → 400 + 칸별 오류 목록 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage());
        problem.setProperty("fieldErrors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .toList());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    private ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.name());
        return problem;
    }

    record FieldErrorResponse(String field, String message) {
    }
}
