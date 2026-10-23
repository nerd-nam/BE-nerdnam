package com.nerdnam.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nerdnam.common.auth.CurrentMemberId;
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
            Class<?> type = WebApiContractDocument.recordType(entry.getKey());
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
