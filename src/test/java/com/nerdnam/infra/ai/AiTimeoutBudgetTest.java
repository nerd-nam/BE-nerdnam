package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.aijob.application.AiJobProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 오래 멈춘 작업 정리({@code stale-after})가 아직 돌고 있는 작업을 실패로 지우지 않는다 (SPEC 이슈 #91).
 *
 * <p>AI 서버는 읽기 제한 직전까지 기다렸다 503 을 줄 수 있고, BE 는 503 이면 1회 다시 보낸다.
 * 그래서 작업 하나의 최악은 (그 작업이 차례로 부르는 읽기 제한의 합) × 시도 횟수다.
 * 읽기 제한만 올리고 {@code stale-after} 를 잊으면 이 테스트가 실패한다. 실제 application.yml 을 읽는다.
 */
class AiTimeoutBudgetTest {

    @Test
    void 정리_시간은_어느_작업의_최악보다도_길다() throws IOException {
        Binder binder = binder();
        AiServerProperties ai = binder.bind("nerdnam.ai", AiServerProperties.class).get();
        Duration staleAfter = binder.bind("nerdnam.ai-job", AiJobProperties.class).get().staleAfter();

        // 분석은 네 단계를 차례로 부른다 (FitAnalysisPipeline). 나머지 작업은 호출 1번
        List<List<AiEndpoint>> jobs = List.of(
                List.of(AiEndpoint.REQUIREMENTS_EXTRACT, AiEndpoint.EMBED, AiEndpoint.MATCH, AiEndpoint.GAP_GUIDE),
                List.of(AiEndpoint.RESTRUCTURE),
                List.of(AiEndpoint.GUIDE));
        for (List<AiEndpoint> job : jobs) {
            Duration worst = job.stream().map(ai::readTimeout).reduce(Duration.ZERO, Duration::plus)
                    .multipliedBy(HttpAiServerClient.MAX_ATTEMPTS);
            assertThat(staleAfter).as("%s 의 최악 %s", job, worst).isGreaterThan(worst);
        }
    }

    @Test
    void 읽기_시간_제한이_계약_C04_와_같다() throws IOException {
        // AI 서버는 계약에 적힌 읽기 제한보다 10초 먼저 끝낸다(기획서 v1.65). BE 설정이 계약과 다르면
        // AI 가 BE 보다 훨씬 먼저 포기하거나(SPEC #91: /embed 8초 vs 120초) BE 가 먼저 끊어 usage 를 잃는다
        AiServerProperties ai = binder().bind("nerdnam.ai", AiServerProperties.class).get();
        Map<String, Duration> contract = contractReadLimits();

        assertThat(contract).as("계약 설명문의 타임아웃 줄").isNotEmpty();
        for (AiEndpoint endpoint : AiEndpoint.values()) {
            assertThat(ai.readTimeout(endpoint)).as("%s 읽기 제한", endpoint.path())
                    .isEqualTo(contract.get(endpoint.path()));
        }
    }

    /** 계약 설명문의 "- 타임아웃: 연결 2초. 읽기 `/health` 2초 · `/embed` 120초 · …" 줄 */
    private static Map<String, Duration> contractReadLimits() throws IOException {
        String yaml = new ClassPathResource("contracts/api-ai.openapi.yaml").getContentAsString(StandardCharsets.UTF_8);
        String line = yaml.lines().filter(l -> l.contains("- 타임아웃:")).findFirst().orElse("");
        Map<String, Duration> limits = new LinkedHashMap<>();
        Matcher m = Pattern.compile("`(/[a-z/-]+)` (\\d+)초").matcher(line);
        while (m.find()) {
            limits.put(m.group(1), Duration.ofSeconds(Long.parseLong(m.group(2))));
        }
        return limits;
    }

    private static Binder binder() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        // 프로필 문서가 따로 있으면 첫 문서(기본값)만 본다
        return new Binder(ConfigurationPropertySources.from(yaml.getFirst()));
    }
}
