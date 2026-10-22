package com.nerdnam.infra.ai;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/** 모든 AI 호출(상태 확인 제외)을 ai_call_log 에 남기는 감싸기(데코레이터). 실제·스텁 클라이언트 모두 이것으로 감싼다 */
class RecordingAiServerClient implements AiServerClient {

    private final AiServerClient delegate;
    private final AiCallLog callLog;

    RecordingAiServerClient(AiServerClient delegate, AiCallLog callLog) {
        this.delegate = delegate;
        this.callLog = callLog;
    }

    @Override
    public AiServerHealth health() {
        return delegate.health();
    }

    @Override
    public Embeddings embed(List<String> texts, EmbedPurpose purpose) {
        return recorded("EMBED", () -> delegate.embed(texts, purpose), Embeddings::embeddingModel, r -> null);
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        return recorded("RESTRUCTURE", () -> delegate.restructure(request),
                RestructureResult::model, RestructureResult::promptVersion);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        return recorded("EXTRACT_REQUIREMENTS", () -> delegate.extractRequirements(request),
                RequirementExtraction.Result::model, RequirementExtraction.Result::promptVersion);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        return recorded("MATCH", () -> delegate.match(request), MatchCall.Result::model, MatchCall.Result::promptVersion);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        return recorded("GAP_GUIDE", () -> delegate.gapGuide(request),
                GapGuideCall.Result::model, GapGuideCall.Result::promptVersion);
    }

    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        return recorded("GUIDE", () -> delegate.guide(request), GuideCall.Result::model, GuideCall.Result::promptVersion);
    }

    private <T> T recorded(String operation, Supplier<T> call, Function<T, String> model, Function<T, String> prompt) {
        long started = System.nanoTime();
        AiCallContext context = AiCallContext.current();
        try {
            T result = call.get();
            callLog.record(context, operation, model.apply(result), prompt.apply(result), true, null, elapsedMs(started));
            return result;
        } catch (AiServerUnavailableException e) {
            callLog.record(context, operation, null, null, false, e.reason().name(), elapsedMs(started));
            throw e;
        } catch (RuntimeException e) {
            callLog.record(context, operation, null, null, false, "AI_ERROR", elapsedMs(started));
            throw e;
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
