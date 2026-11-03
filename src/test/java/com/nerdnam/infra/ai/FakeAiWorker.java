package com.nerdnam.infra.ai;

import io.lettuce.core.RedisClient;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 테스트용 가짜 AI 작업자. 요청 스트림 3개를 읽어 받은 메시지를 모으고, {@code reply} 가 돌려준 결과들을
 * 메시지의 {@code reply_to} 스트림에 넣는다 (결과가 없으면 아무것도 넣지 않는다 — 메시지가 사라진 경우).
 */
final class FakeAiWorker implements AutoCloseable {

    record Received(String stream, Map<String, String> fields) {
    }

    final List<Received> received = new CopyOnWriteArrayList<>();
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final Thread thread;
    private volatile boolean running = true;

    FakeAiWorker(String redisUri, Function<Received, List<Map<String, String>>> reply) {
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.thread = Thread.ofPlatform().daemon().start(() -> loop(reply));
    }

    @SuppressWarnings("unchecked")
    private void loop(Function<Received, List<Map<String, String>>> reply) {
        String llm = "0-0";
        String embed = "0-0";
        String embedBg = "0-0";
        while (running) {
            try {
                List<StreamMessage<String, String>> messages = connection.sync().xread(
                        XReadArgs.Builder.block(Duration.ofMillis(200)),
                        XReadArgs.StreamOffset.from(QueueAiServerClient.LLM, llm),
                        XReadArgs.StreamOffset.from(QueueAiServerClient.EMBED, embed),
                        XReadArgs.StreamOffset.from(QueueAiServerClient.EMBED_BG, embedBg));
                for (StreamMessage<String, String> m : messages) {
                    switch (m.getStream()) {
                        case QueueAiServerClient.LLM -> llm = m.getId();
                        case QueueAiServerClient.EMBED -> embed = m.getId();
                        default -> embedBg = m.getId();
                    }
                    Received r = new Received(m.getStream(), m.getBody());
                    received.add(r);
                    for (Map<String, String> result : reply.apply(r)) {
                        connection.sync().xadd(m.getBody().get("reply_to"), result);
                    }
                }
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
            }
        }
    }

    /** 성공 결과 하나 */
    static Map<String, String> ok(Received r, String body) {
        return Map.of("id", r.fields().get("id"), "status", "200", "body", body);
    }

    long countWithId(String id) {
        return received.stream().filter(r -> id.equals(r.fields().get("id"))).count();
    }

    @Override
    public void close() throws InterruptedException {
        running = false;
        thread.join(2000);
        connection.close();
        client.shutdown();
    }
}
