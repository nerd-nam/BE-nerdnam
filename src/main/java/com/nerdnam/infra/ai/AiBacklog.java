package com.nerdnam.infra.ai;

/**
 * AI 쪽이 지금 새 작업을 받을 수 있는지 (BE #66·#67). AI 작업 접수 때 본다.
 *
 * <p>큐 경로에서는 큐 대기가 실패가 아니라서, 막지 않으면 몰릴 때 사용자는 "진행 중"을 오래 보다가 결국
 * {@code TIMEOUT} 을 받는다. 그리고 큐 Redis 는 넘쳐도 지우지 않으므로({@code noeviction}) 메모리에 닿으면
 * 넣기 자체가 실패한다. 그보다 앞에서 "잠시 뒤 다시 시도해 주세요"로 바로 알려 주는 편이 낫다.
 */
public interface AiBacklog {

    /** 막을 것이 없다 — HTTP 경로·스텁 */
    AiBacklog NONE = () -> false;

    /** true 면 새 AI 작업을 받지 않는다 */
    boolean full();
}
