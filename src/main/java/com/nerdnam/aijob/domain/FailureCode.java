package com.nerdnam.aijob.domain;

/** 실패 이유. 웹은 GPU_UNAVAILABLE 이면 "잠시 후 다시 시도해 주세요"를 띄운다 (C-02 공통 3) */
public enum FailureCode {
    GPU_UNAVAILABLE,
    AI_ERROR,
    TIMEOUT,
    INTERNAL
}
