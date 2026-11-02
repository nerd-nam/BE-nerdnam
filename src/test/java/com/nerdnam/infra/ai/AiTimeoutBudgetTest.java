package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.nerdnam.aijob.application.AiJobProperties;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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

    private static Binder binder() throws IOException {
        List<PropertySource<?>> yaml = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        // 프로필 문서가 따로 있으면 첫 문서(기본값)만 본다
        return new Binder(ConfigurationPropertySources.from(yaml.getFirst()));
    }
}
