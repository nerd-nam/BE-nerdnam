package com.nerdnam.infra.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 AI 서버를 부르는 구현. 연결 실패·5xx 는 한 번만 다시 시도하고(작업목록 BE-05),
 * 그래도 실패하면 이유를 나눠 {@link AiServerUnavailableException}으로 바꾼다.
 */
public class HttpAiServerClient implements AiServerClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAiServerClient.class);
    private static final int MAX_ATTEMPTS = 2;
    private static final JsonMapper GUIDE_JSON = JsonMapper.builder().build();

    private final RestClient restClient;

    public HttpAiServerClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public static HttpAiServerClient create(AiServerProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        return new HttpAiServerClient(restClient);
    }

    @Override
    public AiServerHealth health() {
        try {
            HealthResponse response = restClient.get().uri("/health").retrieve().body(HealthResponse.class);
            Map<String, String> models = response == null || response.models() == null ? Map.of() : response.models();
            boolean ready = !models.isEmpty() && models.values().stream().allMatch("ready"::equalsIgnoreCase);
            return new AiServerHealth(ready, models);
        } catch (RestClientException e) {
            log.debug("AI server health check failed", e);
            return AiServerHealth.unreachable();
        }
    }

    @Override
    public Embeddings embed(List<String> texts, EmbedPurpose purpose) {
        EmbedRequest request = new EmbedRequest(texts, purpose.name().toLowerCase(Locale.ROOT));
        EmbedResponse response = call("embed", () -> restClient.post()
                .uri("/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(EmbedResponse.class));
        if (response == null || response.vectors() == null || response.vectors().size() != texts.size()) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, "embed",
                    new IllegalStateException("벡터 개수가 요청한 텍스트 수와 달라요."));
        }
        List<float[]> vectors = response.vectors().stream().map(HttpAiServerClient::toArray).toList();
        return new Embeddings(vectors, response.embeddingModel());
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        return post("restructure", "/restructure", request, RestructureResult.class);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        return post("requirements/extract", "/requirements/extract", request, RequirementExtraction.Result.class);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        return post("match", "/match", request, MatchCall.Result.class);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        return post("gap-guide", "/gap-guide", request, GapGuideCall.Result.class);
    }

    /**
     * /guide 는 SSE 로 응답한다. 웹으로 중계하지 않으므로 끝까지 받아 모은 뒤 마지막 {@code event: result}의
     * data 를 결과로 쓴다. AI 서버가 SSE 대신 JSON 한 덩어리로 돌려줘도 받는다.
     */
    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        String body = call("guide", () -> restClient.post()
                .uri("/guide")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class));
        try {
            return GUIDE_JSON.readValue(ServerSentEvents.resultData(body), GuideCall.Result.class);
        } catch (RuntimeException e) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, "guide", e);
        }
    }

    private <T> T post(String operation, String path, Object request, Class<T> type) {
        T response = call(operation, () -> restClient.post()
                .uri(path)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(type));
        if (response == null) {
            throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, operation,
                    new IllegalStateException("빈 응답"));
        }
        return response;
    }

    /** 연결 실패·5xx 는 한 번 더 시도한다. 4xx 는 요청이 잘못된 것이라 다시 시도하지 않는다 */
    private <T> T call(String operation, Supplier<T> request) {
        RestClientException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return request.get();
            } catch (ResourceAccessException | HttpServerErrorException e) {
                last = e;
                log.warn("AI server call failed ({} attempt {}/{}): {}", operation, attempt, MAX_ATTEMPTS, e.getMessage());
            } catch (RestClientException e) {
                throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, operation, e);
            }
        }
        throw new AiServerUnavailableException(classify(last), operation, last);
    }

    static AiServerUnavailableException.Reason classify(RestClientException e) {
        if (e instanceof HttpServerErrorException server) {
            return server.getStatusCode().value() == HttpStatus.SERVICE_UNAVAILABLE.value()
                    ? AiServerUnavailableException.Reason.GPU_UNAVAILABLE   // 서버는 떴지만 모델이 아직 준비 안 됨
                    : AiServerUnavailableException.Reason.AI_ERROR;
        }
        Throwable cause = e.getCause();
        if (cause instanceof ConnectException) {
            return AiServerUnavailableException.Reason.GPU_UNAVAILABLE;
        }
        if (cause instanceof SocketTimeoutException timeout) {
            String message = timeout.getMessage() == null ? "" : timeout.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("connect")
                    ? AiServerUnavailableException.Reason.GPU_UNAVAILABLE   // 연결 시간 초과 = 서버에 닿지 못함
                    : AiServerUnavailableException.Reason.TIMEOUT;
        }
        return AiServerUnavailableException.Reason.GPU_UNAVAILABLE;
    }

    private static float[] toArray(List<Float> values) {
        float[] array = new float[values.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    // ---- AI 서버 요청·응답 (계약 C-04 전 임시 모양, 기획서 5.5 기준) ----

    record EmbedRequest(List<String> texts, String purpose) {
    }

    record EmbedResponse(List<List<Float>> vectors, @JsonProperty("embedding_model") String embeddingModel) {
    }

    record HealthResponse(String status, Map<String, String> models) {
    }
}
