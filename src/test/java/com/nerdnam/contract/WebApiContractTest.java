package com.nerdnam.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.common.auth.CurrentMemberId;
import com.nerdnam.guide.domain.GuideTab;
import com.nerdnam.support.ApiTestSupport;
import io.swagger.v3.oas.models.media.StringSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.junit.jupiter.api.Test;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/** 실제 MVC 매핑에서 계약을 만들고 커밋한 복사본과 비교한다. 운영 의존성은 늘리지 않는다. */
@SpringBootTest(properties = {"nerdnam.auth.mode=session", "nerdnam.ai-job.sync=true",
        "springdoc.use-fqn=true", "springdoc.override-with-generic-response=false",
        "springdoc.writer-with-order-by-keys=true", "springdoc.paths-to-match=/api/**"})
class WebApiContractTest extends ApiTestSupport {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final YAMLMapper YAML = YAMLMapper.builder().disable(YAMLWriteFeature.MINIMIZE_QUOTES)
            .enable(YAMLWriteFeature.LITERAL_BLOCK_STYLE).disable(YAMLWriteFeature.SPLIT_LINES)
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER).build();
    private static final Path COPY = Path.of("contracts/web-api.openapi.yaml");

    @Autowired
    JsonMapper appJson;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentMemberId.class)
                .replaceWithSchema(YearMonth.class, new StringSchema().pattern("^[0-9]{4}-(0[1-9]|1[0-2])$").example("2026-10"));
    }

    @Test
    void 웹_계약이_현재_컨트롤러와_같다() throws Exception {
        String response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        ObjectNode contract = (ObjectNode) JSON.readTree(response);
        contract = WebApiContractDocument.complete(contract, appJson, mappings);
        Path generated = Path.of("build/contracts/web-api.openapi.yaml");
        Files.createDirectories(generated.getParent());
        Files.writeString(generated, YAML.writeValueAsString(contract));
        if (Boolean.getBoolean("nerdnam.contract.update")) {
            Files.createDirectories(COPY.getParent());
            Files.copy(generated, COPY, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        assertThat(Files.exists(COPY)).as("./gradlew exportWebApiContract로 계약을 먼저 내보내세요").isTrue();
        JsonNode saved = YAML.readTree(Files.readString(COPY));
        assertThat(contract).as("계약을 검토하고 ./gradlew exportWebApiContract로 갱신하세요").isEqualTo(saved);
    }

    @Test
    void 모든_API_경로와_참조가_있고_인증_주입값을_요청하지_않는다() throws Exception {
        JsonNode contract = YAML.readTree(Files.readString(COPY));
        Set<String> actual = new HashSet<>();
        contract.path("paths").properties().forEach(path -> path.getValue().properties().forEach(method -> {
            actual.add(method.getKey() + " " + path.getKey());
            method.getValue().path("parameters").forEach(parameter ->
                    assertThat(parameter.path("name").asString()).isNotIn("memberId", "token"));
        }));
        Set<String> expected = new HashSet<>(List.of("post /api/auth/logout", "get /oauth2/authorization/{provider}"));
        mappings.getHandlerMethods().forEach((mapping, handler) -> mapping.getPatternValues().stream()
                .filter(path -> path.startsWith("/api/"))
                .forEach(path -> mapping.getMethodsCondition().getMethods().forEach(method ->
                        expected.add(method.name().toLowerCase(Locale.ROOT) + " " + path))));
        assertThat(actual).isEqualTo(expected);
        checkReferences(contract, contract);
    }

    @Test
    void 계약_enum은_실제_응답_매퍼의_값과_같다() throws Exception {
        JsonNode contract = YAML.readTree(Files.readString(COPY));
        contract.path("components").path("schemas").properties().forEach(entry -> {
            Class<?> type = javaType(entry.getValue());
            if (type == null || !type.isRecord()) {
                return;
            }
            for (var field : type.getRecordComponents()) {
                if (field.getType().isEnum()) {
                    JsonNode property = entry.getValue().path("properties").path(field.getName());
                    if (property.has("anyOf")) {
                        property = property.path("anyOf").get(0);
                    }
                    assertThat(property.path("enum")).as("%s.%s", entry.getKey(), field.getName())
                            .isEqualTo(appJson.valueToTree(field.getType().getEnumConstants()));
                }
            }
        });
    }

    /**
     * 스키마 이름은 짧고(FE 가 이 이름으로 타입을 만든다), 어느 Java 타입인지는 {@code x-java-class} 로 남긴다.
     * C-03 검토에서 FE 가 요청한 것 (SPEC PR #47 의견 3).
     */
    @Test
    void 스키마_이름은_짧고_자바_타입을_확장으로_남긴다() throws Exception {
        JsonNode schemas = YAML.readTree(Files.readString(COPY)).path("components").path("schemas");
        Set<String> withoutJavaType = new HashSet<>();
        schemas.properties().forEach(entry -> {
            assertThat(entry.getKey()).as("스키마 이름에 패키지가 남았어요").doesNotContain(".");
            if (entry.getValue().path("x-java-class").isMissingNode()) {
                withoutJavaType.add(entry.getKey());
            } else {
                assertThat(javaType(entry.getValue())).as("x-java-class %s", entry.getKey()).isNotNull();
            }
        });
        assertThat(withoutJavaType).as("직접 만든 스키마만 Java 타입이 없다").containsExactly("ProblemDetail");
    }

    /**
     * 응답은 키가 항상 있지만(required), <b>값이 null일 수 있는 것은 {@code @Nullable} 로 표시한 것만</b>이다.
     * 전부 null 가능이면 웹의 타입이 모두 "없을 수도 있음"이 된다 (SPEC PR #47 의견 2).
     */
    @Test
    void 응답의_필수_필드는_표시한_것만_null이_된다() throws Exception {
        JsonNode schemas = YAML.readTree(Files.readString(COPY)).path("components").path("schemas");
        List<String> wrong = new java.util.ArrayList<>();
        schemas.properties().forEach(entry -> {
            Class<?> type = javaType(entry.getValue());
            if (type == null || !type.isRecord() || type.getSimpleName().endsWith("Request")) {
                return;
            }
            for (var field : type.getRecordComponents()) {
                JsonNode property = entry.getValue().path("properties").path(field.getName());
                if (property.isMissingNode()) {
                    continue;
                }
                boolean nullable = property.path("anyOf").toString().contains("\"null\"");
                boolean marked = java.util.Arrays.stream(field.getAnnotatedType().getAnnotations())
                        .anyMatch(a -> a.annotationType().getSimpleName().equals("Nullable"));
                if (nullable != marked) {
                    wrong.add("%s.%s (계약 null 가능=%s, @Nullable=%s)"
                            .formatted(entry.getKey(), field.getName(), nullable, marked));
                }
            }
        });
        assertThat(wrong).as("계약의 null 허용과 @Nullable 표시가 어긋났어요").isEmpty();
    }

    /**
     * 작성 방향 탭은 요청 경로와 응답이 같은 이름을 쓴다 — {@code motive·intro·portfolio·career·image}.
     * 전에는 응답이 enum 이름(motivation 등)이라 웹이 같은 탭을 두 이름으로 다뤘다 (SPEC PR #47 의견 1).
     */
    @Test
    void 작성_방향_탭은_요청_경로와_응답이_같은_이름이다() throws Exception {
        JsonNode schemas = YAML.readTree(Files.readString(COPY)).path("components").path("schemas");
        JsonNode expected = appJson.valueToTree(java.util.Arrays.stream(GuideTab.values()).map(GuideTab::path).toList());
        for (String schema : List.of("Guide", "TabStatus")) {
            for (String field : List.of("tab", "path")) {
                assertThat(schemas.path(schema).path("properties").path(field).path("enum"))
                        .as("%s.%s", schema, field).isEqualTo(expected);
            }
        }
        JsonNode parameter = YAML.readTree(Files.readString(COPY)).path("paths")
                .path("/api/job-postings/{postingId}/guides/{tab}").path("get").path("parameters");
        assertThat(parameter.toString()).as("경로의 tab 설명").contains("motive");
    }

    /** 짧은 스키마 이름에서 Java 타입을 찾는다 (계약에 적어 둔 x-java-class) */
    private static Class<?> javaType(JsonNode schema) {
        JsonNode name = schema.path("x-java-class");
        return name.isString() ? WebApiContractDocument.recordType(name.asString()) : null;
    }

    private static void checkReferences(JsonNode root, JsonNode value) {
        if (value.has("$ref")) {
            String reference = value.path("$ref").asString();
            assertThat(reference).startsWith("#/components/");
            assertThat(root.at(reference.substring(1)).isMissingNode()).as("참조 %s", reference).isFalse();
        }
        if (value.isObject() || value.isArray()) {
            value.forEach(child -> checkReferences(root, child));
        }
    }
}
