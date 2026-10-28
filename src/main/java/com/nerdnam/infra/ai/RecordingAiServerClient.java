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
    public Embeddings embed(List<EmbedItem> items, EmbedPurpose purpose) {
        // /embed 는 LLM 을 부르지 않아 계약에 usage 가 없다 → null 로 남긴다 (0 이 아니다)
        return recorded("EMBED", () -> delegate.embed(items, purpose), Embeddings::embeddingModel, r -> null, r -> null);
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        return recorded("RESTRUCTURE", () -> delegate.restructure(request),
                RestructureResult::model, RestructureResult::promptVersion, RestructureResult::usage);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        return recorded("EXTRACT_REQUIREMENTS", () -> delegate.extractRequirements(request),
                RequirementExtraction.Result::model, RequirementExtraction.Result::promptVersion,
                RequirementExtraction.Result::usage);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        return recorded("MATCH", () -> delegate.match(request), MatchCall.Result::model, MatchCall.Result::promptVersion,
                MatchCall.Result::usage);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        return recorded("GAP_GUIDE", () -> delegate.gapGuide(request),
                GapGuideCall.Result::model, GapGuideCall.Result::promptVersion,
                GapGuideCall.Result::usage);
    }

    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        return recorded("GUIDE", () -> delegate.guide(request), GuideCall.Result::model, GuideCall.Result::promptVersion,
                GuideCall.Result::usage);
    }

    /**
     * 기록 한 줄 = 재시도를 포함한 호출 1건. 토큰은 <b>실패한 시도까지 더해</b> 남긴다 (BE-20):
     * 마지막 응답의 {@code usage} + {@link AiUsageTally} 가 모아 둔 실패한 시도의 {@code usage}.
     * 끝까지 실패하면 모아 둔 값만 남는다 (그마저 없으면 null — "모름").
     */
    private <T> T recorded(String operation, Supplier<T> call, Function<T, String> model, Function<T, String> prompt,
                           Function<T, AiUsage> usage) {
        long started = System.nanoTime();
        AiCallContext context = AiCallContext.current();
        AiUsageTally.start();
        try {
            T result = call.get();
            AiUsage total = AiUsage.sum(AiUsageTally.take(), usage.apply(result));
            callLog.record(context, operation, model.apply(result), prompt.apply(result), true, null,
                    elapsedMs(started), total);
            return result;
        } catch (AiServerUnavailableException e) {
            callLog.record(context, operation, null, null, false, e.reason().name(), elapsedMs(started),
                    AiUsageTally.take());
            throw e;
        } catch (RuntimeException e) {
            callLog.record(context, operation, null, null, false, "AI_ERROR", elapsedMs(started), AiUsageTally.take());
            throw e;
        }
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }
}
