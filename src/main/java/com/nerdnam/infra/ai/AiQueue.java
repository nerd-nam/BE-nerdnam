package com.nerdnam.infra.ai;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AI 작업 큐(Redis Streams)와 주고받는 부분 (ADR-0009, BE #66).
 *
 * <pre>
 * 보내기: XADD ai:req:{llm|embed|embed-bg} id op body traceparent enqueued_at reply_to
 * 받기:   이 Pod 의 결과 스트림(reply_to = ai:res:{Pod})을 스레드 하나가 XREAD 로 읽어, 기다리는 호출을 id 로 깨운다
 * </pre>
 * 결과 스트림은 Pod 마다 하나라 소비자 그룹이 필요 없다. 결과가 그 호출을 기다리지 않는 Pod 로 가는 일도 없다.
 *
 * <p><b>중복 결과:</b> AI 는 최소 1회 처리라 같은 id 의 결과가 두 번 올 수 있다(회수·다시 넣기). 기다리는 호출이
 * 없는 id 의 결과는 버린다 — 먼저 온 결과 하나만 반영된다.
 *
 * <p>세션 Redis 와 섞이지 않게 Spring 의 {@code RedisConnectionFactory} 빈을 만들지 않고 Lettuce 를 직접 쓴다.
 * 그 빈을 하나 더 만들면 세션 자동 설정이 어느 Redis 를 쓸지 바뀐다.
 */
public class AiQueue implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AiQueue.class);
    private static final Duration READ_BLOCK = Duration.ofSeconds(1);
    private static final int READ_COUNT = 100;

    private final AiQueueProperties properties;
    private final RedisClient client;
    /** XADD 등 짧은 명령 — 여러 작업 스레드가 함께 쓴다 (Lettuce 연결은 스레드 안전). 처음 보낼 때 연결한다 */
    private volatile StatefulRedisConnection<String, String> commands;
    /** 결과 읽기 전용 — XREAD BLOCK 이 연결을 붙잡으므로 따로 둔다. 읽는 스레드만 쓴다 */
    private StatefulRedisConnection<String, String> reader;
    private final Map<String, CompletableFuture<Result>> waiting = new ConcurrentHashMap<>();
    private final Thread readerThread;
    private volatile boolean running = true;

    /** AI 서버가 돌려준 결과 하나 */
    public record Result(String id, int status, String body, String detail, String usage) {
    }

    public AiQueue(AiQueueProperties properties) {
        this.properties = properties;
        RedisURI.Builder uri = RedisURI.builder().withHost(properties.host()).withPort(properties.port())
                .withTimeout(properties.timeout());
        if (!AiQueueProperties.blank(properties.password())) {
            if (AiQueueProperties.blank(properties.username())) {
                uri.withPassword(properties.password().toCharArray());
            } else {
                uri.withAuthentication(properties.username(), properties.password());
            }
        }
        // 시작할 때 연결하지 않는다 — 큐 Redis 가 잠깐 없어도 앱은 떠야 한다. 보낼 때 실패하면 그 작업만 실패하고,
        // 읽는 스레드는 연결될 때까지 다시 시도한다
        this.client = RedisClient.create(uri.build());
        this.readerThread = Thread.ofPlatform().daemon().name("ai-queue-results").unstarted(this::readLoop);
        this.readerThread.start();
    }

    /** 이 호출의 결과를 기다리기 시작한다. 보내기 <b>전에</b> 불러야 빠른 결과를 놓치지 않는다 */
    public Waiting expect(String id) {
        CompletableFuture<Result> future = new CompletableFuture<>();
        waiting.put(id, future);
        return new Waiting(id, future);
    }

    /** 요청 스트림에 넣는다. 실패하면 Redis 예외를 그대로 던진다 */
    public void send(String stream, Map<String, String> fields) {
        commands().sync().xadd(stream, fields);
    }

    private StatefulRedisConnection<String, String> commands() {
        StatefulRedisConnection<String, String> connection = commands;
        if (connection == null) {
            synchronized (this) {
                if (commands == null) {
                    commands = client.connect();
                }
                connection = commands;
            }
        }
        return connection;
    }

    /**
     * 사용자가 기다리는 요청 스트림({@code ai:req:llm}·{@code ai:req:embed})에 남은 메시지 수. 문서 임베딩
     * ({@code ai:req:embed-bg})은 세지 않는다 — 뒤에서 천천히 돌아도 되는 일이라 사용자 작업을 막을 이유가 없다.
     *
     * <p>AI 가 처리한 요청을 지운다는 전제다(SPEC #101 "처리한 쪽이 지움"). 실패하면 Redis 예외를 그대로 던진다.
     */
    public long backlog() {
        var sync = commands().sync();
        return sync.xlen(QueueAiServerClient.LLM) + sync.xlen(QueueAiServerClient.EMBED);
    }

    /** 이 큐가 결과를 받는 스트림 — 요청 메시지의 {@code reply_to} */
    public String replyStream() {
        return properties.replyStream();
    }

    /** 결과를 기다리는 호출 하나. 끝나면 {@link #close()} 로 등록을 지운다 */
    public final class Waiting implements AutoCloseable {

        private final String id;
        private final CompletableFuture<Result> future;

        private Waiting(String id, CompletableFuture<Result> future) {
            this.id = id;
            this.future = future;
        }

        /** @return 결과. 시간 안에 오지 않으면 null */
        public Result await(Duration timeout) throws InterruptedException {
            try {
                return future.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                return null;
            } catch (ExecutionException e) {
                throw new IllegalStateException(e.getCause());
            }
        }

        @Override
        public void close() {
            waiting.remove(id, future);
        }
    }

    @SuppressWarnings("unchecked")   // XReadArgs.StreamOffset 가변 인자
    private void readLoop() {
        String stream = properties.replyStream();
        String lastId = "0-0";
        while (running) {
            try {
                if (reader == null) {
                    reader = client.connect();
                    reader.setTimeout(properties.timeout().plus(READ_BLOCK));
                }
                List<StreamMessage<String, String>> messages = reader.sync().xread(
                        XReadArgs.Builder.block(READ_BLOCK).count(READ_COUNT),
                        XReadArgs.StreamOffset.from(stream, lastId));
                if (messages == null || messages.isEmpty()) {
                    continue;
                }
                for (StreamMessage<String, String> message : messages) {
                    lastId = message.getId();
                    deliver(message.getBody());
                }
                // 읽은 결과는 지운다(본문에 이력 원문이 들어 있다). 키에는 만료를 걸어 내려간 Pod 의 스트림이 남지 않게 한다
                reader.sync().xdel(stream, messages.stream().map(StreamMessage::getId).toArray(String[]::new));
                reader.sync().expire(stream, properties.resultTtl());
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("Could not read AI queue results from {}: {}", stream, e.getMessage());
                pause();
            }
        }
    }

    private void deliver(Map<String, String> fields) {
        String id = fields.get("id");
        CompletableFuture<Result> future = id == null ? null : waiting.remove(id);
        if (future == null) {
            // 이미 받은 결과(중복)이거나, 기다리다 포기한 호출(TIMEOUT)의 늦은 결과다
            log.debug("Ignoring AI queue result nobody waits for: {}", id);
            return;
        }
        int status;
        try {
            status = Integer.parseInt(fields.getOrDefault("status", ""));
        } catch (NumberFormatException e) {
            status = 502;   // 형식이 어긋난 결과는 AI 오류로 본다
        }
        future.complete(new Result(id, status, fields.get("body"), fields.get("detail"), fields.get("usage")));
    }

    private static void pause() {
        try {
            Thread.sleep(READ_BLOCK);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        running = false;
        readerThread.interrupt();
        try {
            readerThread.join(READ_BLOCK.multipliedBy(3).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (commands != null) {
            commands.close();
        }
        if (reader != null) {
            reader.close();
        }
        client.shutdown();
    }
}
