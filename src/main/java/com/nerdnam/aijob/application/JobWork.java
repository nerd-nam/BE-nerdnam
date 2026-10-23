package com.nerdnam.aijob.application;

import com.nerdnam.aijob.domain.FailureCode;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AI 작업 하나의 세 단계 (01 문서 7장 "접수 → 별도 스레드 AI 호출 → 반영").
 *
 * @param prepare   트랜잭션 ①: 도메인 검사 + AI 입력 만들기 (작업 행 저장과 같은 트랜잭션)
 * @param call      트랜잭션 밖: AI 호출. 오래 걸리므로 DB 연결을 잡지 않는다
 * @param apply     트랜잭션 ②: 결과 반영. 돌려준 id 가 작업의 result_id (웹이 다시 조회할 대상)
 * @param onFailure 실패했을 때 대상 쪽 정리 (예: 적합도 분석을 FAILED 로). 작업 실패 기록과 같은 트랜잭션
 */
public record JobWork<T, R>(Supplier<T> prepare, Function<T, R> call, Function<R, Long> apply,
                            Consumer<FailureCode> onFailure) {

    public static <T, R> JobWork<T, R> of(Supplier<T> prepare, Function<T, R> call, Function<R, Long> apply) {
        return new JobWork<>(prepare, call, apply, code -> { });
    }

    public JobWork<T, R> onFailure(Consumer<FailureCode> handler) {
        return new JobWork<>(prepare, call, apply, handler);
    }
}
