package com.nerdnam.infra.ai;

import com.nerdnam.infra.ai.AiServerUnavailableException.Reason;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 호출을 Redis Streams 큐로 보내는 구현 (ADR-0009, BE #66). {@code nerdnam.ai.queue.enabled=true} 일 때 쓴다.
 *
 * <p>호출마다 요청 스트림에 넣고, <b>결과가 올 때까지 이 스레드에서 기다린다</b>. AI 작업 스레드는 가상 스레드라
 * 기다리는 동안 OS 스레드를 쥐지 않는다 — 그래서 BE 스레드 수가 아니라 AI 가 큐에서 꺼내는 양이 속도를 정한다.
 * 분석·작성 방향·다시 나누기 코드는 HTTP 일 때와 같다.
 *
 * <pre>
 * ai:req:llm      requirements/extract · restructure · match · gap-guide · guide
 * ai:req:embed    요건·질의 임베딩 (사용자가 기다린다)
 * ai:req:embed-bg 문서 임베딩 (확정 후 백그라운드) — AI 가 ai:req:embed 를 먼저 꺼낸다
 * </pre>
 *
 * <p>큐 대기는 실패가 아니다. 다만 끝없이 기다리지 않는다.
 * <ul>
 *   <li>{@code resend-after} 동안 결과가 없으면 <b>같은 id</b>로 다시 넣는다 — 큐 Redis 는 디스크에 저장하지 않아
 *       재시작하면 메시지가 사라진다. AI 는 같은 id 를 한 번만 실행한다(멱등).</li>
 *   <li>{@code wait-timeout} 을 넘으면 TIMEOUT 으로 끝낸다.</li>
 * </ul>
 *
 * <p>결과 {@code status}: 200 성공 / 422 요청 검증 실패 / 502 모델·응답 형식·설정 오류. 503·504 는 HTTP 경로
 * 전용이라 오지 않는다. 200 이 아니면 모두 AI_ERROR 다(HTTP 경로의 4xx·502 와 같다). 실패 결과의 {@code usage} 도
 * 호출 1건의 토큰 합에 더한다 (BE-20).
 */
public class QueueAiServerClient implements AiServerClient {

    private static final Logger log = LoggerFactory.getLogger(QueueAiServerClient.class);
    private static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)   // AI 가 필드를 더해도 깨지지 않게
            .build();

    static final String LLM = "ai:req:llm";
    static final String EMBED = "ai:req:embed";
    static final String EMBED_BG = "ai:req:embed-bg";

    private final AiQueue queue;
    private final AiQueueProperties properties;
    /** /health 는 큐로 보내지 않는다 — AI 서버가 떠 있는지는 HTTP 로 본다 */
    private final AiServerClient health;
    private final ObservationRegistry observations;
    /** 지금 span 의 W3C traceparent. 없으면 null */
    private final Supplier<String> traceparent;
    private final Clock clock;

    public QueueAiServerClient(AiQueue queue, AiQueueProperties properties, AiServerClient health,
                               ObservationRegistry observations, Supplier<String> traceparent) {
        this(queue, properties, health, observations, traceparent, Clock.systemUTC());
    }

    QueueAiServerClient(AiQueue queue, AiQueueProperties properties, AiServerClient health,
                        ObservationRegistry observations, Supplier<String> traceparent, Clock clock) {
        this.queue = queue;
        this.properties = properties;
        this.health = health;
        this.observations = observations;
        this.traceparent = traceparent;
        this.clock = clock;
    }

    @Override
    public AiServerHealth health() {
        return health.health();
    }

    @Override
    public Embeddings embed(List<EmbedItem> items, EmbedPurpose purpose) {
        HttpAiServerClient.EmbedRequest request =
                new HttpAiServerClient.EmbedRequest(purpose.name().toLowerCase(Locale.ROOT), items);
        String stream = purpose == EmbedPurpose.DOCUMENT ? EMBED_BG : EMBED;
        return HttpAiServerClient.embeddings(
                call(AiEndpoint.EMBED, stream, request, HttpAiServerClient.EmbedResponse.class), items.size());
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        return call(AiEndpoint.RESTRUCTURE, LLM, request, RestructureResult.class);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        return call(AiEndpoint.REQUIREMENTS_EXTRACT, LLM, request, RequirementExtraction.Result.class);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        return call(AiEndpoint.MATCH, LLM, request, MatchCall.Result.class);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        return call(AiEndpoint.GAP_GUIDE, LLM, request, GapGuideCall.Result.class);
    }

    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        return call(AiEndpoint.GUIDE, LLM, request, GuideCall.Result.class);
    }

    /**
     * 큐 호출 하나를 span({@code nerdnam.ai.queue})으로 감싼다. 메시지의 traceparent 가 이 span 을 가리켜
     * AI 의 {@code queue.wait}·Bedrock span 이 BE 작업 span 아래로 이어진다.
     */
    private <T> T call(AiEndpoint endpoint, String stream, Object request, Class<T> type) {
        return Observation.createNotStarted("nerdnam.ai.queue", observations)
                .contextualName("ai-queue " + endpoint.operation())
                .lowCardinalityKeyValue("ai.operation", endpoint.operation())
                .lowCardinalityKeyValue("ai.stream", stream)
                .observe(() -> send(endpoint, stream, request, type));
    }

    private <T> T send(AiEndpoint endpoint, String stream, Object request, Class<T> type) {
        String operation = endpoint.operation();
        Map<String, String> message = message(operation, request);
        AiQueue.Result result;
        try (AiQueue.Waiting waiting = queue.expect(message.get("id"))) {
            result = await(stream, message, waiting, operation);
        }
        if (result.status() != 200) {
            // 실패한 호출도 토큰을 썼다 — 호출 1건의 합에 넣는다 (BE-20)
            AiUsageTally.addFailedAttempt(usage(result.usage()));
            log.warn("AI queue call failed ({} {}): status {} {}", operation, result.id(), result.status(),
                    result.detail());
            throw new AiServerUnavailableException(Reason.AI_ERROR, operation,
                    new IllegalStateException("status " + result.status() + ": " + result.detail()));
        }
        try {
            T response = JSON.readValue(result.body(), type);
            if (response == null) {
                throw new IllegalStateException("빈 응답");
            }
            return response;
        } catch (RuntimeException e) {
            throw new AiServerUnavailableException(Reason.AI_ERROR, operation, e);
        }
    }

    private AiQueue.Result await(String stream, Map<String, String> message, AiQueue.Waiting waiting,
                                 String operation) {
        Instant deadline = clock.instant().plus(properties.waitTimeout());
        put(stream, message, operation);
        try {
            while (true) {
                Duration left = Duration.between(clock.instant(), deadline);
                if (left.isNegative() || left.isZero()) {
                    log.warn("AI queue call timed out ({} {}) after {}", operation, message.get("id"),
                            properties.waitTimeout());
                    throw new AiServerUnavailableException(Reason.TIMEOUT, operation,
                            new IllegalStateException("결과를 " + properties.waitTimeout() + " 동안 받지 못했어요."));
                }
                AiQueue.Result result = waiting.await(min(left, properties.resendAfter()));
                if (result != null) {
                    return result;
                }
                if (clock.instant().isBefore(deadline)) {
                    // 메시지가 사라졌을 수 있다(큐 Redis 재시작). 같은 id 라 AI 는 이미 처리 중이면 다시 하지 않는다
                    log.info("No AI queue result for {} {} in {}; sending it again", operation, message.get("id"),
                            properties.resendAfter());
                    put(stream, message, operation);
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();   // 종료 중 — 작업은 실패로 정리된다
            throw new AiServerUnavailableException(Reason.GPU_UNAVAILABLE, operation, interrupted);
        }
    }

    private void put(String stream, Map<String, String> message, String operation) {
        try {
            queue.send(stream, message);
        } catch (RuntimeException e) {
            // 큐 Redis 에 닿지 못함 = 지금 AI 를 쓸 수 없음. 웹은 "잠시 뒤 다시 시도해 주세요"를 띄운다
            log.warn("Could not enqueue AI call ({} {}): {}", operation, message.get("id"), e.getMessage());
            throw new AiServerUnavailableException(Reason.GPU_UNAVAILABLE, operation, e);
        }
    }

    private Map<String, String> message(String operation, Object request) {
        Map<String, String> message = new LinkedHashMap<>();
        // 다시 넣어도 같은 id 다 (AI 멱등 키). 호출마다 새로 만든다
        message.put("id", operation + ":" + UUID.randomUUID());
        message.put("op", operation);
        message.put("body", JSON.writeValueAsString(request));
        String parent = traceparent.get();
        if (parent != null) {
            message.put("traceparent", parent);
        }
        message.put("enqueued_at", clock.instant().toString());
        message.put("reply_to", queue.replyStream());
        return message;
    }

    private static AiUsage usage(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(json, AiUsage.class);
        } catch (RuntimeException e) {
            log.debug("Could not read usage from AI queue result", e);
            return null;
        }
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
