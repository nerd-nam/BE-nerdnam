package com.nerdnam.infra.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 AI 서버를 부르는 구현. 실패하면 이유를 나눠 {@link AiServerUnavailableException}으로 바꾼다 (작업목록 BE-05).
 *
 * <p>다시 시도 규칙 (기획서 5.5 "AI 서버 공통 규칙"): 연결 실패·연결 시간 초과·503·그 밖의 5xx 는 한 번 더 시도한다.
 * 4xx 는 요청이 잘못된 것이라 하지 않고, <b>읽기 시간 초과도 하지 않는다</b> — 같은 요청을 다시 보내면 GPU 부하만 두 배가 된다.
 *
 * <p>읽기 시간 제한은 엔드포인트마다 다르므로({@code /guide} 120초 등) 제한값별로 {@link RestClient}를 따로 만들어 둔다.
 */
public class HttpAiServerClient implements AiServerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiServerClient.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final JsonMapper GUIDE_JSON = JsonMapper.builder().build();

    private final Map<AiEndpoint, RestClient> clients;

    HttpAiServerClient(Map<AiEndpoint, RestClient> clients) {
        this.clients = new EnumMap<>(clients);
    }

    /** 테스트용 — 모든 엔드포인트에 같은 클라이언트를 쓴다 */
    public HttpAiServerClient(RestClient restClient) {
        Map<AiEndpoint, RestClient> all = new EnumMap<>(AiEndpoint.class);
        for (AiEndpoint endpoint : AiEndpoint.values()) {
            all.put(endpoint, restClient);
        }
        this.clients = all;
    }

    public static HttpAiServerClient create(AiServerProperties properties) {
        Map<AiEndpoint, RestClient> clients = new EnumMap<>(AiEndpoint.class);
        for (AiEndpoint endpoint : AiEndpoint.values()) {
            clients.put(endpoint, restClient(properties, properties.readTimeout(endpoint)));
        }
        return new HttpAiServerClient(clients);
    }

    private static RestClient restClient(AiServerProperties properties, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public AiServerHealth health() {
        try {
            HealthResponse response = client(AiEndpoint.HEALTH).get()
                    .uri(AiEndpoint.HEALTH.path()).retrieve().body(HealthResponse.class);
            Map<String, String> models = response == null || response.models() == null ? Map.of() : response.models();
            // 모든 모델이 ready 여야 준비된 것으로 본다. loading·error·stub 은 아직 쓸 수 없다 (기획서 5.5)
            boolean ready = !models.isEmpty() && models.values().stream().allMatch("ready"::equalsIgnoreCase);
            return new AiServerHealth(ready, models);
        } catch (RestClientException e) {
            log.debug("AI server health check failed", e);
            return AiServerHealth.unreachable();
        }
    }

    @Override
    public Embeddings embed(List<EmbedItem> items, EmbedPurpose purpose) {
        EmbedRequest request = new EmbedRequest(purpose.name().toLowerCase(Locale.ROOT), items);
        EmbedResponse response = call(AiEndpoint.EMBED, () -> client(AiEndpoint.EMBED).post()
                .uri(AiEndpoint.EMBED.path())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbedResponse.class));
        if (response == null || response.vectors() == null || response.vectors().size() != items.size()) {
            throw embedError("벡터 개수가 요청한 텍스트 수와 달라요.");
        }
        if (response.dim() != null && response.vectors().stream().anyMatch(v -> v.size() != response.dim())) {
            throw embedError("벡터 차원이 응답의 dim 과 달라요.");
        }
        List<float[]> vectors = response.vectors().stream().map(HttpAiServerClient::toArray).toList();
        return new Embeddings(vectors, response.embeddingModel());
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        return post(AiEndpoint.RESTRUCTURE, request, RestructureResult.class);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        return post(AiEndpoint.REQUIREMENTS_EXTRACT, request, RequirementExtraction.Result.class);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        return post(AiEndpoint.MATCH, request, MatchCall.Result.class);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        return post(AiEndpoint.GAP_GUIDE, request, GapGuideCall.Result.class);
    }

    /**
     * /guide 는 SSE 로 응답한다. 웹으로 중계하지 않으므로 끝까지 받아 모은 뒤 마지막 {@code event: result}의
     * data 를 결과로 쓴다. AI 서버가 SSE 대신 JSON 한 덩어리로 돌려줘도 받는다.
     */
    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        String body = call(AiEndpoint.GUIDE, () -> client(AiEndpoint.GUIDE).post()
                .uri(AiEndpoint.GUIDE.path())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class));
        try {
            return GUIDE_JSON.readValue(ServerSentEvents.resultData(body), GuideCall.Result.class);
        } catch (RuntimeException e) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR,
                    AiEndpoint.GUIDE.operation(), e);
        }
    }

    private RestClient client(AiEndpoint endpoint) {
        return clients.get(endpoint);
    }

    private <T> T post(AiEndpoint endpoint, Object request, Class<T> type) {
        T response = call(endpoint, () -> client(endpoint).post()
                .uri(endpoint.path())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(type));
        if (response == null) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, endpoint.operation(),
                    new IllegalStateException("빈 응답"));
        }
        return response;
    }

    private <T> T call(AiEndpoint endpoint, Supplier<T> request) {
        RestClientException last = null;
        AiServerUnavailableException.Reason reason = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (RestClientException e) {
                last = e;
                reason = classify(e);
                log.warn("AI server call failed ({} attempt {}/{}, {}): {}",
                        endpoint.operation(), attempt, MAX_ATTEMPTS, reason, e.getMessage());
                if (!retryable(e, reason)) {
                    break;
                }
            }
        }
        throw new AiServerUnavailableException(reason, endpoint.operation(), last);
    }

    /**
     * 연결 실패·5xx(503 포함)는 한 번 더 시도한다. 4xx 는 요청이 잘못된 것이라 하지 않고,
     * 읽기 시간 초과도 하지 않는다 — 같은 요청을 다시 보내면 GPU 부하만 두 배가 된다 (기획서 5.5).
     */
    private static boolean retryable(RestClientException e, AiServerUnavailableException.Reason reason) {
        if (reason == AiServerUnavailableException.Reason.TIMEOUT) {
            return false;
        }
        return e instanceof HttpServerErrorException
                || reason == AiServerUnavailableException.Reason.GPU_UNAVAILABLE;
    }

    /**
     * 실패 이유 가르기 (기획서 5.5). 읽기 시간 초과는 응답을 읽는 중에 터지므로 {@link ResourceAccessException}이
     * 아닌 평범한 {@link RestClientException}으로 올 수 있다 → 원인 사슬을 끝까지 따라간다.
     */
    static AiServerUnavailableException.Reason classify(RestClientException e) {
        if (e instanceof HttpServerErrorException server) {
            return server.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                    ? AiServerUnavailableException.Reason.GPU_UNAVAILABLE   // 서버는 떴지만 모델이 아직 준비 안 됨
                    : AiServerUnavailableException.Reason.AI_ERROR;
        }
        if (e instanceof HttpClientErrorException) {
            return AiServerUnavailableException.Reason.AI_ERROR;             // 422 등 요청 형식 오류 = API 쪽 문제
        }
        for (Throwable cause = e.getCause(); cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException timeout) {
                String message = timeout.getMessage() == null ? "" : timeout.getMessage().toLowerCase(Locale.ROOT);
                return message.contains("connect")
                        ? AiServerUnavailableException.Reason.GPU_UNAVAILABLE   // 연결 시간 초과 = 서버에 닿지 못함
                        : AiServerUnavailableException.Reason.TIMEOUT;
            }
            if (cause instanceof ConnectException) {
                return AiServerUnavailableException.Reason.GPU_UNAVAILABLE;
            }
        }
        // 연결 단계에서 터진 그 밖의 네트워크 오류는 서버가 꺼진 것으로, 응답을 읽다 터진 것은 AI 오류로 본다
        return e instanceof ResourceAccessException
                ? AiServerUnavailableException.Reason.GPU_UNAVAILABLE
                : AiServerUnavailableException.Reason.AI_ERROR;
    }

    private static AiServerUnavailableException embedError(String message) {
        return new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR,
                AiEndpoint.EMBED.operation(), new IllegalStateException(message));
    }

    private static float[] toArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    // ---- AI 서버 요청·응답 (계약 C-04 전 임시 모양, 기획서 5.5 기준) ----

    record EmbedRequest(String purpose, List<EmbedItem> items) {
    }

    record EmbedResponse(List<List<Float>> vectors, @JsonProperty("embedding_model") String embeddingModel,
                         Integer dim) {
    }

    record HealthResponse(String status, Map<String, String> models) {
    }
}
