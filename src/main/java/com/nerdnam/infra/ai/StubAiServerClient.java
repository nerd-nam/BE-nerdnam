package com.nerdnam.infra.ai;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 개발·테스트용 가짜 AI 서버 (local 프로필 기본, nerdnam.ai.stub=true).
 * 같은 입력에는 항상 같은 결과를 돌려주므로 테스트 결과가 매번 같다. 규칙은 단순하다:
 * <ul>
 *   <li>재구성: 빈 줄로 나뉜 문단 하나 = 사례 하나(최대 3개). 숫자가 없는 문단은 "결과 수치" 확인 질문을 단다.
 *       피드백에 "합쳐"가 있으면 하나로 합친다.</li>
 *   <li>요건 추출: 영문 기술명 덩어리와 "N년"을 뽑고, 아래 표에 있는 약어는 확장어를 붙인다(계약 v0.3.0 `expansions`).</li>
 *   <li>판정: 후보 청크에 요건 기술명이 들어 있으면 충족, 기술명이 없는 요건에 후보가 있으면 부분 충족, 아니면 미충족.</li>
 * </ul>
 *
 * <p>진짜 AI 서버와 같은 <b>입력 규칙</b>(기획서 v1.20)을 검사해 422 대신 {@code AI_ERROR}로 실패시킵니다.
 * 잘못된 입력을 로컬 개발·테스트에서 바로 잡기 위한 것입니다.
 */
public class StubAiServerClient implements AiServerClient {

    public static final int DIMENSIONS = 768;   // embeddinggemma-2 와 같은 차원 (rag.chunk.embedding)
    public static final String MODEL = "stub-embedding:768";
    public static final String LLM = "stub-llm";
    public static final String PROMPT_VERSION = "stub-v1";

    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern YEARS = Pattern.compile("(\\d+)\\s*년");
    private static final Pattern SKILL = Pattern.compile("[A-Za-z][A-Za-z0-9+#.\\-]*(?:\\s[A-Z][A-Za-z0-9+#.\\-]*)*");
    private static final int MAX_EPISODES = 3;

    /**
     * 약어 확장어 (계약 v0.3.0). 진짜 AI 는 줄의 문맥을 보고 만들지만, 스텁은 자주 쓰는 약어만 표로 가진다 —
     * 검색 질의 확장(기획서 5.4)이 로컬·테스트에서도 실제로 동작하는지 보기 위해서다. 줄마다 최대 3개.
     */
    private static final Map<String, List<String>> EXPANSIONS = new LinkedHashMap<>();   // 순서를 지켜야 결과가 같다

    static {
        EXPANSIONS.put("MSA", List.of("Microservice Architecture", "마이크로서비스 아키텍처"));
        EXPANSIONS.put("K8S", List.of("Kubernetes"));
        EXPANSIONS.put("RDBMS", List.of("Relational Database Management System", "관계형 데이터베이스 관리 시스템"));
        EXPANSIONS.put("CI/CD", List.of("Continuous Integration", "Continuous Delivery"));
        EXPANSIONS.put("API", List.of("Application Programming Interface"));
    }
    private static final int MAX_EXPANSIONS = 3;

    /** /restructure 의 mode 와 /guide 의 tab 은 이 값만 받는다 (기획서 v1.20) */
    private static final Set<String> MODES =
            Set.of(RestructureRequest.MODE_INITIAL, RestructureRequest.MODE_SPLIT, RestructureRequest.MODE_EPISODE);
    private static final Set<String> TABS = Set.of("motive", "intro", "portfolio", "career", "image");

    @Override
    public AiServerHealth health() {
        return new AiServerHealth(true, Map.of("llm", "ready", "embedding", "ready"));
    }

    @Override
    public Embeddings embed(List<EmbedItem> items, EmbedPurpose purpose) {
        requireItems(items, AiEndpoint.EMBED);
        List<float[]> vectors = items.stream().map(item -> vectorOf(item.text(), purpose)).toList();
        return new Embeddings(vectors, MODEL);
    }

    @Override
    public RestructureResult restructure(RestructureRequest request) {
        checkRestructureInput(request);
        if (RestructureRequest.MODE_EPISODE.equals(request.mode())) {
            return new RestructureResult(List.of(revise(request)), LLM, PROMPT_VERSION);
        }
        List<int[]> paragraphs = paragraphs(request.sourceText());
        boolean merge = request.feedback() != null && request.feedback().contains("합쳐");
        if (merge && !paragraphs.isEmpty()) {
            paragraphs = List.of(new int[] {paragraphs.getFirst()[0], paragraphs.getLast()[1]});
        }
        List<AiEpisode> episodes = new ArrayList<>();
        for (int[] span : paragraphs.subList(0, Math.min(MAX_EPISODES, paragraphs.size()))) {
            episodes.add(episodeOf(request, span));
        }
        return new RestructureResult(episodes, LLM, PROMPT_VERSION);
    }

    @Override
    public RequirementExtraction.Result extractRequirements(RequirementExtraction.Request request) {
        requireItems(request.lines(), AiEndpoint.REQUIREMENTS_EXTRACT);
        List<RequirementExtraction.Extracted> lines = request.lines().stream().map(line -> {
            Set<String> skills = new LinkedHashSet<>();
            Matcher m = SKILL.matcher(line.text());
            while (m.find()) {
                skills.add(m.group().strip());
            }
            Matcher years = YEARS.matcher(line.text());
            Integer minYears = years.find() ? Integer.valueOf(years.group(1)) : null;
            return new RequirementExtraction.Extracted(line.id(), List.copyOf(skills), expansions(line.text()),
                    minYears);
        }).toList();
        return new RequirementExtraction.Result(lines, LLM, PROMPT_VERSION);
    }

    @Override
    public MatchCall.Result match(MatchCall.Request request) {
        requireItems(request.requirements(), AiEndpoint.MATCH);
        List<MatchCall.Judgement> results = request.requirements().stream().map(req -> {
            List<String> skills = req.skills() == null ? List.of() : req.skills();
            List<Long> hits = req.candidates().stream()
                    .filter(c -> skills.stream().anyMatch(s -> contains(c.text(), s)))
                    .map(MatchCall.Candidate::chunkId)
                    .limit(5)
                    .toList();
            if (!hits.isEmpty()) {
                return new MatchCall.Judgement(req.id(), "met", hits, "요건 기술을 쓴 경험이 있어요.");
            }
            if (skills.isEmpty() && !req.candidates().isEmpty()) {
                return new MatchCall.Judgement(req.id(), "partial", List.of(req.candidates().getFirst().chunkId()),
                        "관련 있어 보이는 경험이 있지만 직접 근거는 약해요.");
            }
            return new MatchCall.Judgement(req.id(), "unmet", List.of(), "근거가 되는 경험을 찾지 못했어요.");
        }).toList();
        return new MatchCall.Result(results, LLM, PROMPT_VERSION);
    }

    @Override
    public GapGuideCall.Result gapGuide(GapGuideCall.Request request) {
        requireItems(request.requirements(), AiEndpoint.GAP_GUIDE);
        request.requirements().stream()
                .filter(r -> !"partial".equals(r.verdict()) && !"unmet".equals(r.verdict()))
                .findFirst()
                .ifPresent(r -> reject(AiEndpoint.GAP_GUIDE, "미충족·부분 충족 요건만 보낼 수 있어요: " + r.verdict()));
        List<GapGuideCall.Guide> guides = request.requirements().stream().map(req -> {
            boolean hasAdjacent = req.adjacent() != null && !req.adjacent().isEmpty();
            String advice = hasAdjacent
                    ? "'" + req.text() + "' 요건은 가까운 경험을 연결해 어떤 점이 통하는지 설명해 보세요."
                    : "'" + req.text() + "' 요건은 지금 학습 중이라면 학습 계획과 함께 정직하게 적어 보세요.";
            List<Long> cited = hasAdjacent ? List.of(req.adjacent().getFirst().chunkId()) : List.of();
            return new GapGuideCall.Guide(req.id(), advice, "해 보지 않은 경험을 해 본 것처럼 쓰지 마세요.", cited);
        }).toList();
        return new GapGuideCall.Result(guides, LLM, PROMPT_VERSION);
    }

    @Override
    public GuideCall.Result guide(GuideCall.Request request) {
        if (!TABS.contains(request.tab())) {
            reject(AiEndpoint.GUIDE, "없는 탭 이름이에요: " + request.tab());
        }
        List<Long> cited = request.requirements().stream()
                .flatMap(r -> r.evidence() == null ? java.util.stream.Stream.empty() : r.evidence().stream())
                .map(MatchCall.Candidate::chunkId)
                .distinct()
                .limit(5)
                .toList();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("core_message", request.company() + " " + request.position() + "에 맞춘 핵심 메시지");
        content.put("sections", List.of(Map.of("title", "도입", "description", "지원 이유와 핵심 경험 한 가지",
                "sources", cited)));
        content.put("keywords", request.requirements().stream().map(GuideCall.Requirement::text).limit(3).toList());
        content.put("avoid", List.of("근거 없는 수치", "해 보지 않은 경험"));
        if ("portfolio".equals(request.tab())) {
            content.put("pages", List.of(
                    Map.of("order", 2, "title", "아키텍처", "kind", "diagram", "hint", "구성도", "tool", "draw.io"),
                    Map.of("order", 1, "title", "표지", "kind", "cover", "hint", "프로젝트 이름", "tool", "Figma")));
        }
        if (request.feedback() != null && !request.feedback().isBlank()) {
            content.put("feedback_applied", request.feedback());
        }
        return new GuideCall.Result(content, cited, LLM, PROMPT_VERSION);
    }

    /** 줄에 쓰인 약어의 확장어. 줄에 이미 있는 말과 중복은 빼고 최대 3개 (기획서 5.5 AI 쪽 정리 규칙과 같다) */
    private static List<String> expansions(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        EXPANSIONS.forEach((abbreviation, words) -> {
            if (!upper.contains(abbreviation)) {
                return;
            }
            for (String word : words) {
                if (result.size() < MAX_EXPANSIONS && !upper.contains(word.toUpperCase(Locale.ROOT))
                        && !result.contains(word)) {
                    result.add(word);
                }
            }
        });
        return List.copyOf(result);
    }

    // ------------------------------------------------------------------ 입력 규칙 (기획서 v1.20)

    private static void checkRestructureInput(RestructureRequest request) {
        if (!MODES.contains(request.mode())) {
            reject(AiEndpoint.RESTRUCTURE, "없는 mode 예요: " + request.mode());
        }
        if (RestructureRequest.MODE_SPLIT.equals(request.mode())
                && (request.feedback() == null || request.feedback().isBlank())) {
            reject(AiEndpoint.RESTRUCTURE, "다시 나누기(split)에는 피드백이 필요해요.");
        }
    }

    /** 목록 입력(items·lines·requirements)은 1개 이상 */
    private static void requireItems(List<?> items, AiEndpoint endpoint) {
        if (items == null || items.isEmpty()) {
            reject(endpoint, "보낼 항목이 없어요.");
        }
    }

    /** 진짜 AI 서버의 422 와 같은 자리 — 4xx 는 다시 시도하지 않는다 (기획서 5.5 D-7) */
    private static void reject(AiEndpoint endpoint, String reason) {
        throw new AiServerUnavailableException(AiServerUnavailableException.Reason.AI_ERROR, endpoint.operation(),
                new IllegalArgumentException(reason));
    }

    // ------------------------------------------------------------------ 재구성 규칙

    /** 원문(+ 보충 답변)을 빈 줄 기준 문단으로 나눈다. 보충 답변("질문:"으로 시작)은 사례로 만들지 않는다 */
    private static List<int[]> paragraphs(String text) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = Pattern.compile("(?s)(.+?)(?:\\n\\s*\\n|$)").matcher(text);
        while (m.find()) {
            String part = m.group(1);
            if (!part.isBlank() && !part.strip().startsWith("질문:")) {
                int start = m.start(1) + (part.length() - part.stripLeading().length());
                int end = m.start(1) + part.stripTrailing().length();
                spans.add(new int[] {start, end});
            }
        }
        return spans;
    }

    private static AiEpisode episodeOf(RestructureRequest request, int[] span) {
        String paragraph = request.sourceText().substring(span[0], span[1]);
        List<String> sentences = sentences(paragraph);
        String first = sentences.getFirst();
        List<String> skills = request.skills() == null ? List.of()
                : request.skills().stream().filter(s -> contains(paragraph, s)).toList();
        boolean hasNumber = DIGIT.matcher(paragraph).find();
        List<AiEpisode.Metric> metrics = hasNumber
                ? List.of(new AiEpisode.Metric("성과 수치", null, sentences.getLast(), "raw_text"))
                : List.of();
        List<AiEpisode.Clarification> clarifications = hasNumber ? List.of()
                : List.of(new AiEpisode.Clarification("result-metric", "result", "결과를 보여 줄 수치가 있나요?",
                        "예: 응답 시간 1.2초 → 0.3초"));
        return new AiEpisode(cut(first, 40), null, first,
                sentences.size() > 2 ? String.join(" ", sentences.subList(1, sentences.size() - 1)) : "",
                sentences.size() > 1 ? sentences.getLast() : "",
                skills, metrics, List.of(List.of(span[0], span[1])), clarifications);
    }

    private static AiEpisode revise(RestructureRequest request) {
        AiEpisode target = Objects.requireNonNull(request.targetEpisode(), "target_episode");
        List<AiEpisode.Clarification> clarifications = target.clarifications() == null ? List.of() : target.clarifications();
        String result = target.result();
        String solution = target.solution();
        if (request.answer() != null) {
            String question = request.answer().question();
            clarifications = clarifications.stream().filter(c -> !Objects.equals(c.question(), question)).toList();
            result = (result == null || result.isBlank() ? "" : result + " ") + request.answer().answer().strip();
        }
        if (request.feedback() != null && !request.feedback().isBlank()) {
            solution = (solution == null ? "" : solution) + "\n(반영: " + request.feedback().strip() + ")";
        }
        return new AiEpisode(target.title(), target.role(), target.problem(), solution, result, target.skills(),
                target.metrics(), target.sourceSpans(), clarifications);
    }

    private static List<String> sentences(String paragraph) {
        List<String> list = new ArrayList<>();
        for (String s : paragraph.split("(?<=[.!?。])\\s+|\\n")) {
            if (!s.isBlank()) {
                list.add(s.strip());
            }
        }
        return list.isEmpty() ? List.of(paragraph.strip()) : list;
    }

    private static boolean contains(String text, String word) {
        return text != null && word != null && text.toLowerCase(Locale.ROOT).contains(word.toLowerCase(Locale.ROOT));
    }

    private static String cut(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    // ------------------------------------------------------------------ 임베딩

    /** 텍스트 내용으로 시드를 정해 정규분포 값을 뽑고, 길이 1로 정규화한다 (코사인 거리 계산에 맞게) */
    static float[] vectorOf(String text, EmbedPurpose purpose) {
        long seed = 1125899906842597L;
        for (byte b : (purpose.name() + ":" + text).getBytes(StandardCharsets.UTF_8)) {
            seed = 31 * seed + b;
        }
        Random random = new Random(seed);
        float[] vector = new float[DIMENSIONS];
        double norm = 0;
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = (float) random.nextGaussian();
            norm += vector[i] * vector[i];
        }
        float scale = (float) (1.0 / Math.sqrt(norm));
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] *= scale;
        }
        return vector;
    }
}
