package com.nerdnam.aijob.application;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 배포·재시작 때 AI 작업을 안전하게 비우는 장치 (BE #45).
 *
 * <p><b>왜 필요한가:</b> 작업 큐는 메모리에 있고 Pod 가 내려가면 사라진다. DB 의 작업 행은
 * {@code IN_PROGRESS} 로 남지만 아무도 다시 실행하지 않아, 오래 멈춘 작업 정리(기본 20분)가 돌 때까지
 * 사용자는 "만드는 중"만 본다. AI 읽기 시간 제한이 최대 300초라 종료를 기다려 끝내는 것도 현실적이지 않다.
 *
 * <p><b>하는 일</b>
 * <ol>
 *   <li><b>접수 중지</b>({@code drain}) — 새 작업을 {@code AI_UNAVAILABLE}("잠시 뒤 다시 시도해 주세요")로
 *       거절한다. 계약에 있는 코드라 웹은 이미 다루는 상태다. 동시에 readiness 를 내려
 *       Kubernetes 가 트래픽을 끊게 한다.</li>
 *   <li><b>비었는지 알려 준다</b> — 관리 포트의 {@code /actuator/aijob} 으로 큐·실행 중 개수를 본다.
 *       0 이 되면 배포한다.</li>
 *   <li><b>종료 때 정리</b> — 그래도 남은 작업은 {@link AiJobs} 가 실패({@code INTERNAL})로 바꾼다
 *       (대상 쪽 정리까지 해야 해서 거기서 한다). 사용자는 "다시 시도"를 볼 수 있고,
 *       오래 멈춘 작업 정리를 20분 기다리지 않는다.</li>
 * </ol>
 *
 * <p>영속 큐(재시작 후 재개)는 하지 않았다 — 지금 작업은 모두 사용자가 버튼을 눌러 시작하고
 * 다시 시도할 수 있어서, 끊긴 것을 <b>보이게 실패시키는</b> 쪽이 사용자에게 더 정직하다.
 */
@Component
public class AiJobDrain {

    private static final Logger log = LoggerFactory.getLogger(AiJobDrain.class);

    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final ApplicationEventPublisher events;
    /** 이 Pod 가 아직 끝내지 못한 작업 수 — AiJobs 가 알려 준다 */
    private volatile IntSupplier inFlight = () -> 0;

    AiJobDrain(ApplicationEventPublisher events) {
        this.events = events;
    }

    void inFlightSource(IntSupplier source) {
        this.inFlight = source;
    }

    /** 새 작업 접수를 멈추고 트래픽도 받지 않는다 */
    public void drain() {
        if (draining.compareAndSet(false, true)) {
            log.info("AI job intake stopped (draining). In-flight: {}", inFlight.getAsInt());
            events.publishEvent(new AvailabilityChangeEvent<>(this, ReadinessState.REFUSING_TRAFFIC));
        }
    }

    /** 다시 접수한다 (배포를 취소했을 때) */
    public void resume() {
        if (draining.compareAndSet(true, false)) {
            log.info("AI job intake resumed");
            events.publishEvent(new AvailabilityChangeEvent<>(this, ReadinessState.ACCEPTING_TRAFFIC));
        }
    }

    public boolean draining() {
        return draining.get();
    }

    public int inFlight() {
        return inFlight.getAsInt();
    }
}
