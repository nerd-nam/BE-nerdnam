package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.nerdnam.guide.domain.GuideTab;
import com.nerdnam.jd.domain.RequirementType;
import com.nerdnam.matching.domain.EvidenceSourceType;
import com.nerdnam.matching.domain.Verdict;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** C-04 계약을 Spring 없이 대조한다. JSON 이름은 리플렉션 대신 실제 직렬화 결과로 확인한다. */
class ApiAiContractTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().build();
    private static final JsonNode CONTRACT = readYaml("/contracts/api-ai.openapi.yaml");
    private static final JsonNode SCHEMAS = CONTRACT.path("components").path("schemas");

    @Test
    void 생성_엔드포인트에_서버_시간_초과_응답이_명시돼_있다() {
        for (String path : List.of("/restructure", "/requirements/extract", "/match", "/gap-guide", "/guide")) {
            assertThat(CONTRACT.path("paths").path(path).path("post").path("responses").has("504"))
                    .as("%s의 504는 TIMEOUT이며 재시도하지 않는다", path).isTrue();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("records")
    void 요청과_응답의_JSON_필드와_필수_목록이_계약과_같다(String schemaName, Object sample,
                                                     List<String> required) {
        JsonNode schema = SCHEMAS.path(schemaName);
        JsonNode serialized = json(sample);
        assertThat(schema.isMissingNode()).as("계약 스키마 %s", schemaName).isFalse();
        assertThat(serialized.propertyNames()).as("%s JSON 필드", schemaName)
                .containsExactlyInAnyOrderElementsOf(schema.path("properties").propertyNames());
        // record에는 필수 여부 표시가 없다. 합의한 목록을 별도로 고정해 필수 → 선택 변경도 잡는다.
        // 실제 클라이언트가 누락 응답을 거부하는지 확인하는 테스트는 아니다.
        assertThat(strings(schema.path("required"))).as("%s 필수 목록", schemaName)
                .containsExactlyInAnyOrderElementsOf(required);
        for (String name : required) {
            assertThat(serialized.hasNonNull(name)).as("%s.%s 필수 값", schemaName, name).isTrue();
        }
        Object decoded = JSON.readValue(serialized.toString(), sample.getClass());
        assertThat(json(decoded)).as("%s 역직렬화 뒤 필드와 값 보존", schemaName).isEqualTo(serialized);
    }

    static Stream<Arguments> records() {
        var metric = new AiEpisode.Metric("응답 시간", "100ms", "50ms", "raw_text");
        var clarification = new AiEpisode.Clarification("result-metric", "result", "성과는 무엇인가요?", "50% 감소");
        var episode = new AiEpisode("성능 개선", "개발", "느린 조회", "캐시", "50% 감소",
                List.of("Redis"), List.of(metric), List.of(List.of(0, 10)), List.of(clarification));
        var answer = new RestructureRequest.Answer("성과는 무엇인가요?", "50% 감소");
        var item = new EmbedItem("캐시를 적용했다", "성능 개선");
        var candidate = new MatchCall.Candidate(1L, lower(EvidenceSourceType.EPISODE), "캐시를 적용했다");
        var line = new RequirementExtraction.Line(1L, lower(RequirementType.REQUIRED), "Redis 경험");
        var extracted = new RequirementExtraction.Extracted(1L, List.of("Redis"), 1);
        var requirement = new MatchCall.Requirement(1L, lower(RequirementType.REQUIRED), "Redis 경험",
                List.of("Redis"), 1, List.of(candidate));
        var judgement = new MatchCall.Judgement(1L, lower(Verdict.PARTIAL), List.of(1L), "기간 확인이 필요해요");
        var gap = new GapGuideCall.Requirement(1L, lower(RequirementType.REQUIRED), "Redis 경험",
                lower(Verdict.PARTIAL), List.of(candidate));
        var gapGuide = new GapGuideCall.Guide(1L, "기간을 보충하세요", "과장하지 마세요", List.of(1L));
        var advice = new GuideCall.GapGuide("기간을 보충하세요", "과장하지 마세요");
        var guideRequirement = new GuideCall.Requirement(1L, lower(RequirementType.REQUIRED), "Redis 경험",
                lower(Verdict.PARTIAL), "기간 확인이 필요해요", List.of(candidate), advice);
        return Stream.of(
                record("EmbedItem", item, "text"),
                record("EmbedRequest", new HttpAiServerClient.EmbedRequest(lower(EmbedPurpose.DOCUMENT), List.of(item)),
                        "purpose items"),
                record("EmbedResponse", new HttpAiServerClient.EmbedResponse(List.of(List.of(0.1f, 0.2f)), "모델", 2),
                        "embedding_model dim vectors"),
                record("HealthResponse", new HttpAiServerClient.HealthResponse("ok", Map.of("llm", "ready", "embedding", "ready")),
                        "status models"),
                record("Metric", metric, "name"),
                record("Clarification", clarification, "question"),
                record("Episode", episode, "title problem solution result"),
                record("Answer", answer, "question answer"),
                record("RestructureRequest", new RestructureRequest(RestructureRequest.MODE_EPISODE, "프로젝트", "원문",
                        List.of("Redis"), List.of(episode), List.of(episode), episode, "성과 보충", answer), "mode source_text"),
                record("RestructureResponse", new RestructureResult(List.of(episode), "모델", "p1"), "model prompt_version episodes"),
                record("RequirementLine", line, "id type text"),
                record("ExtractRequest", new RequirementExtraction.Request(List.of(line)), "lines"),
                record("ExtractedLine", extracted, "id skills_raw"),
                record("ExtractResponse", new RequirementExtraction.Result(List.of(extracted), "모델", "p2"), "model prompt_version lines"),
                record("Candidate", candidate, "chunk_id chunk_type text"),
                record("MatchRequirement", requirement, "id type text candidates"),
                record("MatchRequest", new MatchCall.Request(List.of(requirement)), "requirements"),
                record("MatchResult", judgement, "id verdict evidence_chunk_ids reason"),
                record("MatchResponse", new MatchCall.Result(List.of(judgement), "모델", "p3"), "model prompt_version results"),
                record("GapRequirement", gap, "id type text verdict"),
                record("GapGuideRequest", new GapGuideCall.Request(List.of(gap), List.of("개발")), "requirements"),
                record("GapGuide", gapGuide, "id advice avoid cited_chunk_ids"),
                record("GapGuideResponse", new GapGuideCall.Result(List.of(gapGuide), "모델", "p6"), "model prompt_version guides"),
                record("GapAdvice", advice, "advice avoid"),
                record("GuideRequirement", guideRequirement, "id type text verdict reason"),
                record("GuideRequest", new GuideCall.Request(GuideTab.MOTIVATION.aiName(), "회사", "개발자", "소개",
                        List.of("개발"), List.of(guideRequirement), "성과 강조"), "tab requirements"),
                record("GuideResponse", guideFixture(GuideTab.MOTIVATION), "content cited_chunk_ids model prompt_version")
        );
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("enums")
    void 허용값은_코드의_상수와_enum에서_가져와_계약과_비교한다(String schema, String field, List<String> values) {
        assertThat(strings(SCHEMAS.path(schema).path("properties").path(field).path("enum")))
                .as("%s.%s 허용값", schema, field).containsExactlyInAnyOrderElementsOf(values);
    }

    static Stream<Arguments> enums() {
        List<String> types = lowerValues(RequirementType.values());
        List<String> verdicts = lowerValues(Verdict.values());
        return Stream.of(
                Arguments.of("RestructureRequest", "mode", List.of(RestructureRequest.MODE_INITIAL,
                        RestructureRequest.MODE_SPLIT, RestructureRequest.MODE_EPISODE)),
                Arguments.of("GuideRequest", "tab", Arrays.stream(GuideTab.values()).map(GuideTab::aiName).toList()),
                Arguments.of("EmbedRequest", "purpose", lowerValues(EmbedPurpose.values())),
                Arguments.of("RequirementLine", "type", types),
                Arguments.of("MatchRequirement", "type", types),
                Arguments.of("GapRequirement", "type", types),
                Arguments.of("GuideRequirement", "type", types),
                Arguments.of("MatchResult", "verdict", verdicts),
                Arguments.of("GuideRequirement", "verdict", verdicts),
                Arguments.of("GapRequirement", "verdict", Arrays.stream(Verdict.values())
                        .filter(v -> v != Verdict.MET).map(ApiAiContractTest::lower).toList()),
                Arguments.of("Candidate", "chunk_type", lowerValues(EvidenceSourceType.values()))
        );
    }

    @ParameterizedTest
    @EnumSource(GuideTab.class)
    void 탭별_공통_예시를_읽고_content를_그대로_돌려준다(GuideTab tab) {
        JsonNode fixture = readJson("/contracts/guide.json").path("guides").path(tab.aiName());
        assertThat(json(guideFixture(tab))).isEqualTo(fixture);
        JsonNode content = fixture.path("content");
        assertThat(content.path("tab").asString()).isEqualTo(tab.aiName());
        String reference = SCHEMAS.path("GuideResponse").path("properties").path("content")
                .path("discriminator").path("mapping").path(tab.aiName()).asString();
        assertThat(reference).startsWith("#/components/schemas/");
        JsonNode schema = CONTRACT.at(reference.substring(1));
        assertThat(schema.path("properties").path("tab").path("const").asString()).isEqualTo(tab.aiName());
        assertThat(schema.path("properties").propertyNames()).containsAll(content.propertyNames());
        for (String required : strings(schema.path("required"))) {
            assertThat(content.hasNonNull(required)).as("%s.content.%s", tab, required).isTrue();
        }
    }

    @Test
    void 제목이_없는_임베딩_항목은_선택_필드를_생략한다() {
        assertThat(json(EmbedItem.of("요건"))).isEqualTo(JSON.readTree("{\"text\":\"요건\"}"));
        assertThat(strings(SCHEMAS.path("EmbedItem").path("required"))).doesNotContain("title");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("copies")
    void 옆에_SPEC이_있으면_복사본이_최신인지_확인한다(String localResource, String sourceFile) throws IOException {
        Path source = Path.of("../SPEC-nerdnam", sourceFile);
        assumeTrue(Files.isDirectory(source.getParent()), "SPEC 체크아웃이 없는 CI에서는 복사본만 대조한다");
        assertThat(source).as("SPEC 원본이 이동하거나 삭제됐는지 확인").exists();
        JsonNode original = sourceFile.endsWith("yaml") ? YAML.readTree(Files.readString(source)) : JSON.readTree(Files.readString(source));
        JsonNode copy = sourceFile.endsWith("yaml") ? CONTRACT : readJson(localResource);
        assertThat(copy).as("SPEC의 %s를 %s로 다시 복사하세요", sourceFile, localResource).isEqualTo(original);
    }

    static Stream<Arguments> copies() {
        return Stream.of(
                Arguments.of("/contracts/api-ai.openapi.yaml", "contracts/api-ai.openapi.yaml"),
                Arguments.of("/contracts/guide.json", "fixtures/guide.json"));
    }

    private static Arguments record(String schema, Object sample, String required) {
        return Arguments.of(schema, sample, List.of(required.split(" ")));
    }

    private static GuideCall.Result guideFixture(GuideTab tab) {
        return JSON.treeToValue(readJson("/contracts/guide.json").path("guides").path(tab.aiName()), GuideCall.Result.class);
    }

    private static JsonNode json(Object value) {
        return JSON.readTree(JSON.writeValueAsString(value));
    }

    private static List<String> strings(JsonNode array) {
        assertThat(array.isArray()).as("계약의 목록: %s", array).isTrue();
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static List<String> lowerValues(Enum<?>[] values) {
        return Arrays.stream(values).map(ApiAiContractTest::lower).toList();
    }

    private static JsonNode readYaml(String resource) {
        try (InputStream stream = ApiAiContractTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("테스트 자원 %s", resource).isNotNull();
            return YAML.readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("계약 파일을 읽지 못했어요: " + resource, e);
        }
    }

    private static JsonNode readJson(String resource) {
        try (InputStream stream = ApiAiContractTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as("테스트 자원 %s", resource).isNotNull();
            return JSON.readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("공통 예시를 읽지 못했어요: " + resource, e);
        }
    }
}
