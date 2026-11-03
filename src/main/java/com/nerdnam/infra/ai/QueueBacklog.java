package com.nerdnam.infra.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 큐 경로의 {@link AiBacklog} — 사용자가 기다리는 요청 스트림에 {@code max-backlog} 보다 많이 쌓였으면 막는다.
 *
 * <p>큐 Redis 에 닿지 못해도 막는다. 그때는 넣기도 실패하므로, 작업을 만들었다가 실패시키는 것보다
 * 처음부터 "잠시 뒤 다시 시도해 주세요"가 낫다.
 */
class QueueBacklog implements AiBacklog {

    private static final Logger log = LoggerFactory.getLogger(QueueBacklog.class);

    private final AiQueue queue;
    private final long max;

    QueueBacklog(AiQueue queue, long max) {
        this.queue = queue;
        this.max = max;
    }

    @Override
    public boolean full() {
        try {
            long waiting = queue.backlog();
            if (waiting >= max) {
                log.warn("AI queue backlog {} reached the limit {}; rejecting new AI jobs", waiting, max);
                return true;
            }
            return false;
        } catch (RuntimeException e) {
            log.warn("Could not read AI queue backlog; rejecting new AI jobs: {}", e.getMessage());
            return true;
        }
    }
}
