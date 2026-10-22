package com.nerdnam.infra.ai;

import com.nerdnam.common.error.BusinessException;
import com.nerdnam.common.error.ErrorCode;

/**
 * AI 서버 호출 실패. reason 은 AI 작업(ai_job)의 failure_code 로 그대로 쓴다.
 * 웹은 GPU_UNAVAILABLE 이면 "잠시 후 다시 시도해 주세요"를 띄운다 (C-02 공통 3).
 */
public class AiServerUnavailableException extends BusinessException {

    public enum Reason {
        GPU_UNAVAILABLE,   // 연결 불가, 또는 모델이 아직 준비 안 됨(503) — GPU 서버가 꺼져 있을 때
        TIMEOUT,           // 연결은 됐지만 응답이 시간 안에 오지 않음
        AI_ERROR           // 그 밖의 AI 서버 오류
    }

    private final Reason reason;
    private final String operation;

    /** 메시지는 화면에 보여줄 문장(ErrorCode 기본 문구)이고, 어느 호출이 왜 실패했는지는 operation·reason·cause 로 남긴다 */
    public AiServerUnavailableException(Reason reason, String operation, Throwable cause) {
        super(ErrorCode.AI_UNAVAILABLE);
        this.reason = reason;
        this.operation = operation;
        if (cause != null) {
            initCause(cause);
        }
    }

    public Reason reason() {
        return reason;
    }

    public String operation() {
        return operation;
    }
}
