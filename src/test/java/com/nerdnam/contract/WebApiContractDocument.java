package com.nerdnam.contract;

import com.nerdnam.aijob.domain.FailureCode;
import com.nerdnam.guide.domain.GuideTab;
import com.nerdnam.jd.domain.RequirementType;
import com.nerdnam.matching.domain.AnalysisStatus;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Springdoc이 알 수 없는 세션·오류·조건부 규칙을 보완한다. 규칙이 바뀌면 계약 diff로 검토한다. */
final class WebApiContractDocument {

    private final ObjectNode document;
    private final ObjectNode schemas;
    private final ObjectNode examples;
    private final JsonMapper json;

    private WebApiContractDocument(ObjectNode document, JsonMapper json) {
        this.document = document;
        this.json = json;
        schemas = (ObjectNode) document.path("components").path("schemas");
        examples = ((ObjectNode) document.path("components")).putObject("examples");
    }

    static ObjectNode complete(ObjectNode document, JsonMapper json, RequestMappingHandlerMapping mappings) {
        var result = new WebApiContractDocument(document, json);
        result.metadata();
        result.schemas();
        result.operations(mappings);
        result.shortenSchemaNames();   // FQCN 으로 하는 일이 모두 끝난 뒤에
        return result.document;
    }

    private void metadata() {
        document.put("openapi", "3.1.0");
        document.putObject("info").put("title", "nerdnam 웹 API").put("version", "0.1.3")
                .put("description", """
                        C-03 웹 ↔ BE 계약. BE의 실제 컨트롤러에서 ./gradlew exportWebApiContract로 내보낸다.
                        JSON 필드는 camelCase, enum 값은 소문자다. ProblemDetail.title 오류 코드는 대문자다.
                        날짜·시각은 ISO 8601, 월은 YYYY-MM. ID는 정수다. 없는 자료와 남의 자료는 모두 404다.
                        인증은 SESSION 쿠키이며 웹은 credentials: include로 요청한다.
                        GET /api/auth/csrf로 토큰·쿠키를 받은 뒤 변경 요청에는 X-XSRF-TOKEN 헤더를 보낸다.
                        로그인·로그아웃 후 CSRF 토큰을 다시 받는다. X-Member-Id는 로컬 개발 전용이며 이 계약의 인증 수단이 아니다.
                        현재 약관 미동의 상태는 403 CONSENT_REQUIRED와 termsVersion을 준다.
                        이때 내 정보 조회·동의·탈퇴·로그아웃·CSRF 준비만 가능하다.
                        AI 작업은 202 + jobId를 받고 GET /api/ai-jobs/{jobId}를 폴링한다. 웹으로 SSE를 보내지 않는다.
                        예시는 형식 확인용 가상 데이터이며 실제 회원·작업 id가 아니다.
                        웹 목업 서버(Prism)가 이 예시로 응답하므로 상태 값과 몸통이 서로 맞게 맞춰 둔다.
                        끝난 상태 예시는 결과가 채워져 있고, 진행 중 예시는 비어 있다.
                        응답은 값이 null이어도 키를 남기므로 모든 응답 필드가 required다.
                        그중 값이 null일 수 있는 것만 anyOf로 null을 함께 적는다. 꼭 있는 값에는 null이 없다.
                        스키마 이름은 짧은 Java 타입 이름이고, 원래 타입은 x-java-class에 남긴다(웹은 x- 확장을 무시한다).
                        작성 방향 탭은 요청 경로와 응답이 같은 이름이다: motive·intro·portfolio·career·image.
                        """);
        document.putArray("servers").addObject().put("url", "/").put("description", "BE 서버의 기준 주소");
        ObjectNode security = ((ObjectNode) document.path("components")).putObject("securitySchemes");
        security.putObject("cookieSession").put("type", "apiKey").put("in", "cookie").put("name", "SESSION");
        security.putObject("csrfToken").put("type", "apiKey").put("in", "header").put("name", "X-XSRF-TOKEN");
    }

    private void schemas() {
        schemas.remove("org.springframework.security.web.csrf.CsrfToken");
        // Swagger 모델은 앱의 Jackson 3 enum 설정을 읽지 않으므로 실제 앱 매퍼로 허용값을 만든다.
        for (String name : new ArrayList<>(schemas.propertyNames())) {
            Class<?> type = recordType(name);
            if (type == null || !type.isRecord()) {
                continue;
            }
            ObjectNode schema = (ObjectNode) schemas.path(name);
            boolean request = type.getSimpleName().endsWith("Request");
            List<String> required = new ArrayList<>();
            schema.path("required").forEach(value -> required.add(value.asString()));
            for (RecordComponent field : type.getRecordComponents()) {
                JsonNode node = schema.path("properties").path(field.getName());
                if (!(node instanceof ObjectNode property)) {
                    continue;
                }
                if (field.getType().isEnum()) {
                    property.set("enum", enumValues(field.getType().getEnumConstants()));
                }
                try {
                    if (type.getDeclaredField(field.getName()).isAnnotationPresent(NotBlank.class)) {
                        property.put("minLength", Math.max(1, property.path("minLength").asInt(0)));
                        property.put("description", "공백만 입력할 수 없습니다.");
                    }
                } catch (NoSuchFieldException e) {
                    throw new IllegalStateException("record 필드를 찾지 못했어요: " + name, e);
                }
                // 응답은 키가 항상 있지만(required) 값이 null일 수 있는 것만 null을 허용한다 — 웹 타입이
                // 전부 "없을 수도 있음"이 되지 않게 @Nullable 로 표시한 것만 (C-03 검토, SPEC PR #47 FE 의견 2).
                // 요청은 필수 검증이 없는 참조 타입에 null을 허용한다.
                boolean nullable = request ? !required.contains(field.getName()) : isNullable(field);
                if (!field.getType().isPrimitive() && nullable) {
                    ObjectNode orNull = json.createObjectNode();
                    orNull.putArray("anyOf").add(property.deepCopy()).addObject().put("type", "null");
                    ((ObjectNode) schema.path("properties")).set(field.getName(), orNull);
                }
                if (!request && !required.contains(field.getName())) {
                    required.add(field.getName());
                }
            }
            schema.set("required", json.valueToTree(required));
        }
        ObjectNode postingStatus = property("com.nerdnam.matching.application.PostingListQuery.PostingSummary", "status");
        ArrayNode states = enumValues(AnalysisStatus.values());
        states.add("draft");
        postingStatus.set("enum", states);
        for (String name : List.of("com.nerdnam.matching.application.FitReportView",
                "com.nerdnam.matching.application.FitReportQuery.AnalysisSummary")) {
            property(name, "failureCode").set("enum", enumValues(FailureCode.values()));
        }
        property("com.nerdnam.matching.application.FitReportView.MatchView", "type")
                .set("enum", enumValues(RequirementType.values()));
        property("com.nerdnam.matching.application.FitReportView.EvidenceView", "reregistering")
                .put("description", "프로젝트·사례 근거는 현재 재등록 여부. true면 S06 프로젝트 카드로 이동한다. 기술·경력은 false.");
        property("com.nerdnam.guide.application.GuideViews.Guide", "content")
                .put("description", "탭별 내용은 C-04 v0.2 GuideResponse.content를 보존하고 cited_chunk_ids를 추가한다. sources의 이름·원본 id는 별도 citations 목록으로 연결한다. 로컬 스텁은 형식이 다를 수 있다.");
        try (var input = WebApiContractDocument.class.getResourceAsStream("/contracts/guide.json")) {
            JsonNode guide = json.readTree(input).path("guides").path("motive");
            ObjectNode content = ((ObjectNode) guide.path("content")).deepCopy();
            content.set("cited_chunk_ids", guide.path("cited_chunk_ids"));
            property("com.nerdnam.guide.application.GuideViews.Guide", "content").set("example", content);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("작성 방향 공통 예시를 읽지 못했어요", e);
        }
        ObjectNode problem = schemas.putObject("ProblemDetail").put("type", "object");
        problem.putArray("required").add("type").add("title").add("status").add("detail");
        ObjectNode fields = problem.putObject("properties");
        fields.putObject("type").put("type", "string").put("example", "about:blank");
        fields.putObject("title").put("type", "string").put("description", "업무 오류 코드는 INVALID_INPUT, CONSENT_REQUIRED, AI_JOB_RUNNING 등 대문자다. HTTP 기본 오류의 title은 Spring 기본 문장일 수 있다.");
        fields.putObject("status").put("type", "integer");
        fields.putObject("detail").put("type", "string");
        fields.putObject("instance").put("type", "string");
        fields.putObject("runningJobId").put("type", "integer").put("format", "int64");
        fields.putObject("termsVersion").put("type", "string");
        ObjectNode fieldErrors = fields.putObject("fieldErrors").put("type", "array").putObject("items").put("type", "object");
        fieldErrors.putObject("properties").putObject("field").put("type", "string");
        ((ObjectNode) fieldErrors.path("properties")).putObject("message").put("type", "string");
        ObjectNode violations = fields.putObject("violations").put("type", "array").putObject("items").put("type", "object");
        ObjectNode violationFields = violations.putObject("properties");
        for (String field : List.of("field", "code", "message")) {
            violationFields.putObject(field).put("type", "string");
        }
        violationFields.putObject("index").putArray("type").add("integer").add("null");
        ObjectNode usage = fields.putObject("usages").put("type", "array").putObject("items").put("type", "object");
        usage.putArray("required").add("type").add("id").add("name");
        ObjectNode usageFields = usage.putObject("properties");
        usageFields.putObject("type").put("type", "string").putArray("enum").add("career").add("project");
        usageFields.putObject("id").put("type", "integer").put("format", "int64");
        usageFields.putObject("name").put("type", "string");
        for (String name : List.of("com.nerdnam.matching.application.FitReportView.EvidenceView",
                "com.nerdnam.guide.application.GuideViews.Citation")) {
            property(name, "deleted").put("description", "삭제한 프로젝트·사례 근거면 true. 이름은 남기고 projectId는 null, 원본 보기 버튼은 숨긴다.");
        }
        problem.put("additionalProperties", true);
    }

    private void operations(RequestMappingHandlerMapping mappings) {
        ObjectNode paths = (ObjectNode) document.path("paths");
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            for (String path : mapping.getPatternValues()) {
                for (var method : mapping.getMethodsCondition().getMethods()) {
                    JsonNode node = paths.path(path).path(method.name().toLowerCase(Locale.ROOT));
                    if (node instanceof ObjectNode operation) {
                        operation.put("operationId", handler.getBeanType().getSimpleName() + "_" + handler.getMethod().getName());
                        operation.put("summary", summary(handler.getBeanType().getSimpleName(), handler.getMethod().getName()));
                    }
                }
            }
        });
        ObjectNode csrf = (ObjectNode) paths.path("/api/auth/csrf").path("get");
        csrf.remove("parameters");
        ObjectNode logout = paths.putObject("/api/auth/logout").putObject("post");
        logout.put("operationId", "logout").put("summary", "로그아웃");
        logout.putObject("responses").putObject("204").put("description", "세션 종료, 응답 본문 없음");
        ObjectNode oauth = paths.putObject("/oauth2/authorization/{provider}").putObject("get");
        oauth.put("operationId", "startSocialLogin").put("summary", "Google·카카오 로그인 시작");
        ObjectNode provider = oauth.putArray("parameters").addObject().put("name", "provider").put("in", "path").put("required", true);
        provider.putObject("schema").put("type", "string").putArray("enum").add("google").add("kakao");
        oauth.putObject("responses").putObject("302").put("description", "소셜 로그인 화면으로 이동. 콜백 처리 후 설정된 웹 주소로 돌아간다.");

        // 동적 ResponseEntity의 성공 코드는 어노테이션만으로 알아낼 수 없다.
        ObjectNode skillResponses = (ObjectNode) paths.path("/api/me/skills").path("post").path("responses");
        skillResponses.set("201", skillResponses.path("200").deepCopy());
        ((ObjectNode) skillResponses.path("200")).put("description", "이미 등록한 기술");
        ((ObjectNode) skillResponses.path("201")).put("description", "새 기술 등록");
        for (String path : List.of("/api/projects/{projectId}/rating", "/api/job-postings/{postingId}/guides/rating")) {
            ((ObjectNode) paths.path(path).path("get").path("responses")).putObject("204").put("description", "평가 없음, 응답 본문 없음");
        }
        ObjectNode commonErrors = ((ObjectNode) document.path("components")).putObject("responses");
        for (int status : List.of(400, 401, 403, 404, 409, 422, 500)) {
            commonErrors.putObject("Error" + status).put("description", switch (status) {
                case 400 -> "입력 오류. fieldErrors 또는 violations를 확인한다.";
                case 401 -> "로그인이 필요하다.";
                case 403 -> "현재 약관 동의 필요(CONSENT_REQUIRED) 또는 CSRF·접근 권한 오류.";
                case 404 -> "없거나 내 소유가 아닌 자료.";
                case 409 -> "버전 충돌, 사용 중인 자료 또는 AI 작업 진행 중. runningJobId로 이어서 조회한다.";
                case 422 -> "현재 상태에서 할 수 없는 동작.";
                default -> "서버 오류.";
            }).putObject("content").putObject("application/problem+json").putObject("schema").put("$ref", "#/components/schemas/ProblemDetail");
        }
        paths.properties().forEach(entry -> entry.getValue().properties().forEach(method -> {
            ObjectNode operation = (ObjectNode) method.getValue();
            boolean publicRoute = entry.getKey().equals("/api/auth/csrf") || entry.getKey().startsWith("/oauth2/");
            ArrayNode security = operation.putArray("security");
            if (!publicRoute) {
                ObjectNode requirement = security.addObject();
                requirement.putArray("cookieSession");
                if (!method.getKey().equals("get")) {
                    requirement.putArray("csrfToken");
                }
            }
            operation.put("description", description(entry.getKey(), method.getKey()));
            JsonNode parameters = operation.path("parameters");
            parameters.forEach(parameter -> {
                if (parameter.path("name").asString().equals("tab")) {
                    ((ObjectNode) parameter.path("schema")).set("enum", json.valueToTree(Arrays.stream(GuideTab.values()).map(GuideTab::path).toList()));
                }
                if (parameter.path("name").asString().equals("category")) {
                    ((ObjectNode) parameter.path("schema")).set("enum", enumValues(com.nerdnam.skill.domain.SkillCategory.values()));
                }
            });
            ObjectNode responses = (ObjectNode) operation.path("responses");
            for (String code : new ArrayList<>(responses.propertyNames())) {
                if (code.equals("204")) {
                    ((ObjectNode) responses.path(code)).remove("content");
                } else {
                    addExample(responses.path(code).path("content"), operation.path("operationId").asString() + "Response");
                }
            }
            addExample(operation.path("requestBody").path("content"), operation.path("operationId").asString() + "Request");
            if (!publicRoute) {
                for (int status : List.of(400, 401, 403, 404, 409, 422, 500)) {
                    responses.putObject(String.valueOf(status)).put("$ref", "#/components/responses/Error" + status);
                }
            }
        }));
    }

    private void addExample(JsonNode content, String fallbackName) {
        if (!(content instanceof ObjectNode media)) {
            return;
        }
        if (media.has("*/*")) {
            media.set("application/json", media.remove("*/*"));
        }
        media.properties().forEach(entry -> {
            ObjectNode value = (ObjectNode) entry.getValue();
            JsonNode schema = value.path("schema");
            String name = schema.has("$ref") ? schema.path("$ref").asString().substring("#/components/schemas/".length()) : fallbackName;
            if (!examples.has(name)) {
                examples.putObject(name).put("summary", "형식 확인용 예시")
                        .set("value", consistentStatus(schema, sample(schema, "", 0)));
            }
            value.putObject("examples").putObject("sample").put("$ref", "#/components/examples/" + name);
        });
    }

    /**
     * 상태 값은 enum 의 첫 값이라 몸통과 어긋날 수 있다. 예를 들어 {@code FitReportView} 는 분석 중이면
     * 충족 수·한 줄 진단·판정이 비는데(실제 응답), 예시는 {@code in_progress} 에 그 값들이 채워져 나왔다.
     * 웹 목업(Prism)이 이 예시를 그대로 응답하므로(FE-03, 기획서 v1.40) 화면이 "분석 중"에서 멈춘다.
     * 그래서 <b>몸통이 채워진 예시는 끝난 상태로</b> 맞춘다.
     */
    private static final Map<String, String> EXAMPLE_STATUS = Map.of(
            "com.nerdnam.matching.application.FitReportView", "completed",
            "com.nerdnam.matching.application.FitReportQuery.AnalysisSummary", "completed",
            "com.nerdnam.matching.application.PostingListQuery.PostingSummary", "completed",
            "com.nerdnam.guide.application.GuideViews.Guide", "completed",
            "com.nerdnam.guide.application.GuideViews.TabStatus", "completed",
            "com.nerdnam.project.application.ProjectViews.Review", "review");

    private JsonNode consistentStatus(JsonNode schema, JsonNode value) {
        // 목록 응답(배열)은 요소 스키마로 찾고 요소마다 맞춘다
        if (value instanceof ArrayNode array) {
            array.forEach(element -> consistentStatus(schema.path("items"), element));
            return array;
        }
        String reference = schema.path("$ref").asString("");
        if (reference.isEmpty() || !(value instanceof ObjectNode object) || !object.has("status")) {
            return value;
        }
        String status = EXAMPLE_STATUS.get(reference.substring(reference.lastIndexOf('/') + 1));
        if (status != null) {
            object.put("status", status);
        }
        return value;
    }

    private JsonNode sample(JsonNode schema, String field, int depth) {
        if (depth > 15) {
            throw new IllegalStateException("예시 스키마에 순환 참조가 있어요");
        }
        if (schema.has("$ref")) {
            return sample(document.at(schema.path("$ref").asString().substring(1)), field, depth + 1);
        }
        if (schema.has("anyOf")) {
            // completedAt 은 끝난 상태 예시(위 EXAMPLE_STATUS)에서 시각이 있어야 앞뒤가 맞는다.
            // finishedAt·resultId 는 AiJobView 예시가 in_progress 라서 null 이 맞다.
            if (List.of("runningJobId", "failureCode", "resultId", "finishedAt", "feedback", "reviewingProjectId").contains(field)) {
                return json.nullNode();
            }
            return sample(schema.path("anyOf").get(0), field, depth + 1);
        }
        if (schema.has("example")) {
            return schema.path("example");
        }
        if (schema.has("enum")) {
            return schema.path("enum").get(0);
        }
        return switch (schema.path("type").asString("object")) {
            case "array" -> json.createArrayNode().add(sample(schema.path("items"), field, depth + 1));
            case "object" -> {
                ObjectNode object = json.createObjectNode();
                schema.path("properties").properties().forEach(entry -> object.set(entry.getKey(), sample(entry.getValue(), entry.getKey(), depth + 1)));
                yield object;
            }
            case "integer", "number" -> json.valueToTree(schema.has("minimum") ? schema.path("minimum").asInt() : field.equals("version") ? 0 : 1);
            case "boolean" -> json.valueToTree(false);
            default -> json.valueToTree(schema.path("format").asString().equals("date-time") ? "2026-10-23T06:00:00Z" : switch (field) {
                case "companyName" -> "예시 회사";
                case "name" -> "Redis";
                case "positionName", "jobTitle" -> "백엔드 개발자";
                case "feedback" -> "성과를 더 강조해 주세요.";
                case "termsVersion" -> "2026-10";
                case "headerName" -> "X-XSRF-TOKEN";
                case "email" -> "example@example.com";
                case "rawText" -> "Redis 캐시를 적용해 응답 시간을 1초에서 0.3초로 줄였습니다.";
                default -> "예시";
            });
        };
    }

    /** jspecify {@code @Nullable}(TYPE_USE)을 붙인 응답 필드만 null이 될 수 있다 */
    private static boolean isNullable(RecordComponent field) {
        for (var annotation : field.getAnnotatedType().getAnnotations()) {
            if (annotation.annotationType().getSimpleName().equals("Nullable")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 스키마 이름을 FQCN 에서 짧은 이름으로 바꾼다 (FE 가 타입을 자동 생성할 때 그 이름을 쓴다).
     * 이름만으로는 어느 Java 타입인지 알 수 없어 {@code x-java-class} 로 남긴다 — 계약 테스트가 이 값으로
     * 실제 enum·필수 목록을 대조한다. 웹의 생성기는 {@code x-} 확장을 무시한다.
     */
    private void shortenSchemaNames() {
        Map<String, String> renamed = new java.util.LinkedHashMap<>();
        for (String name : new ArrayList<>(schemas.propertyNames())) {
            Class<?> type = recordType(name);
            if (type == null) {
                continue;
            }
            String simple = name.substring(name.lastIndexOf('.') + 1);
            if (renamed.containsValue(simple)) {
                throw new IllegalStateException("스키마 이름이 겹쳐요: " + simple + " (" + name + ")");
            }
            renamed.put(name, simple);
        }
        renamed.forEach((fqcn, simple) -> {
            ObjectNode schema = (ObjectNode) schemas.path(fqcn);
            schema.put("x-java-class", recordType(fqcn).getName());
            schemas.set(simple, schema);
            schemas.remove(fqcn);
        });
        rewriteReferences(document, renamed);
    }

    private static void rewriteReferences(JsonNode node, Map<String, String> renamed) {
        if (node instanceof ObjectNode object) {
            JsonNode reference = object.path("$ref");
            if (reference.isString()) {
                String value = reference.asString();
                String prefix = "#/components/schemas/";
                if (value.startsWith(prefix)) {
                    String target = renamed.get(value.substring(prefix.length()));
                    if (target != null) {
                        object.put("$ref", prefix + target);
                    }
                }
            }
        }
        node.forEach(child -> rewriteReferences(child, renamed));
    }

    private ObjectNode property(String schemaName, String name) {
        JsonNode property = schemas.path(schemaName).path("properties").path(name);
        if (property.has("anyOf")) {
            property = property.path("anyOf").get(0);
        }
        if (!(property instanceof ObjectNode result)) {
            throw new IllegalStateException("계약 필드가 없어요: " + schemaName + "." + name);
        }
        return result;
    }

    private ArrayNode enumValues(Object[] values) {
        return (ArrayNode) json.valueToTree(values);
    }

    static Class<?> recordType(String name) {
        String candidate = name;
        while (true) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException e) {
                int dot = candidate.lastIndexOf('.');
                if (dot < 0) {
                    return null;
                }
                candidate = candidate.substring(0, dot) + "$" + candidate.substring(dot + 1);
            }
        }
    }

    private static String summary(String controller, String method) {
        Map<String, String> domains = Map.ofEntries(Map.entry("ProjectController", "프로젝트·사례"),
                Map.entry("MeController", "내 정보·동의"), Map.entry("AuthController", "인증"),
                Map.entry("SkillController", "기술 사전"), Map.entry("UserSkillController", "내 기술스택"),
                Map.entry("CareerController", "경력"), Map.entry("LibraryController", "라이브러리"),
                Map.entry("JobPostingController", "공고"), Map.entry("FitAnalysisController", "분석·리포트"),
                Map.entry("WritingGuideController", "작성 방향"), Map.entry("RatingController", "만족도 평가"),
                Map.entry("AiJobController", "AI 작업"));
        Map<String, String> actions = Map.ofEntries(Map.entry("list", "목록"), Map.entry("get", "조회"),
                Map.entry("create", "등록"), Map.entry("update", "수정"), Map.entry("delete", "삭제"),
                Map.entry("me", "조회"), Map.entry("agree", "동의 기록"), Map.entry("withdraw", "탈퇴"),
                Map.entry("csrf", "CSRF 토큰"), Map.entry("start", "분석 시작"), Map.entry("latest", "최신 리포트"),
                Map.entry("history", "분석 기록"), Map.entry("generate", "탭 생성·재생성"), Map.entry("overview", "탭 상태 목록"),
                Map.entry("autocomplete", "자동완성"), Map.entry("add", "추가"), Map.entry("changeLevel", "숙련도 수정"),
                Map.entry("library", "전체 조회"), Map.entry("onboarding", "온보딩 진행 상태"),
                Map.entry("guideRating", "작성 방향 평가 조회"), Map.entry("projectRating", "사례 생성 평가 조회"),
                Map.entry("rateGuide", "작성 방향 평가 저장"), Map.entry("rateProject", "사례 생성 평가 저장"),
                Map.entry("addSkill", "사용 기술 추가"), Map.entry("answer", "확인 질문 답변"), Map.entry("confirm", "프로젝트 확정"),
                Map.entry("confirmEpisode", "사례 확정"), Map.entry("discardReview", "검토 결과 버리기"),
                Map.entry("drop", "질문을 빼고 진행"), Map.entry("edit", "사례 직접 편집"), Map.entry("excludeEpisode", "사례 제외"),
                Map.entry("reopen", "재등록 시작"), Map.entry("resplit", "다시 나누기"), Map.entry("restore", "사례 버전 복원"),
                Map.entry("review", "검토·읽기 모드 조회"), Map.entry("revise", "사례 수정 요청"), Map.entry("split", "재구성 시작"),
                Map.entry("undo", "사례 다시 검토하기"));
        return domains.getOrDefault(controller, "서비스") + " — " + actions.getOrDefault(method, method);
    }

    private static String description(String path, String method) {
        if (path.equals("/api/me/consents")) {
            return "로그인 전에 동의한 사용자만 로그인 직후 호출한다. 기존 사용자의 약관 버전이 바뀌었으면 현재 약관을 보여주고 다시 동의받은 뒤 호출한다. 본문 없이 현재 termsVersion으로 두 동의 항목을 기록한다. 중복 호출해도 같은 기록을 추가하지 않는다.";
        }
        if (path.endsWith("/guides/{tab}") && method.equals("post")) {
            return "최신 완료 분석의 탭을 만든다. 첫 생성은 feedback을 생략할 수 있다. 같은 분석에서 두 번째 생성부터는 실패 뒤 다시 요청할 때도 공백 아닌 feedback이 필수이며 없으면 400이다. 새 분석이면 새 탭으로 만들므로 첫 생성 규칙을 적용한다. 같은 탭 진행 중에는 409와 runningJobId를 준다.";
        }
        if (path.endsWith("/analyses")) {
            return method.equals("get") ? "이 공고의 분석 기록을 requestedAt·analysisId 내림차순으로 준다. 없으면 빈 배열이다. analysisId로 이전 리포트를 조회한다."
                    : "입력 검증 후 새 분석을 쌓는다. 기존 분석을 덮어쓰지 않는다. 완료되면 작업 resultId가 analysisId다.";
        }
        if (path.endsWith("/report") || path.startsWith("/api/fit-analyses/")) {
            return "근거의 deleted가 true면 이름만 표시하고 원본 이동을 막는다. reregistering이 true인 프로젝트·사례는 S06 프로젝트 카드로 이동한다. false면 S05 읽기 모드로 이동한다. 기술·경력 근거는 S06으로 이동한다. status가 completed가 아니면 집계·진단은 null, matches는 빈 배열이다.";
        }
        if (path.equals("/api/projects/{projectId}") && method.equals("delete")) {
            return "초안·검토·확정·재등록 중 모두 삭제할 수 있다. 해당 프로젝트 재구성 작업 중에는 409 AI_JOB_RUNNING과 runningJobId를 준다. 청크·벡터도 같은 트랜잭션에서 삭제한다. 이전 리포트·작성 방향은 이름과 deleted=true 표시를 남긴다.";
        }
        if (path.endsWith("/review")) {
            return "프로젝트가 confirmed이면 확정한 사례만 읽기 모드로 조회한다. 재등록 중에는 현재 검토 회차를 조회한다.";
        }
        if (path.equals("/api/me")) {
            return "현재 약관 미동의 상태에서도 조회·탈퇴할 수 있다. consentRequired가 true면 동의 안내를 먼저 보여주며, 서비스 API는 403이다.";
        }
        return "세부 상태 규칙은 C-02 화면별 API 목록과 BE docs/02를 따른다. 변경 요청은 세션 쿠키와 CSRF 헤더를 함께 보낸다.";
    }
}
