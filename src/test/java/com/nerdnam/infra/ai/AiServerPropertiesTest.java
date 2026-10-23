package com.nerdnam.infra.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * application.yml 의 `nerdnam.ai.read-timeouts.*` 키가 {@link AiEndpoint} 이름으로 제대로 들어오는지.
 * 키 이름을 틀리면 조용히 기본값(read-timeout)이 쓰이므로 테스트로 고정한다.
 */
class AiServerPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void 하이픈_키도_엔드포인트로_읽는다() {
        runner.withPropertyValues(
                        "nerdnam.ai.base-url=http://ai.test",
                        "nerdnam.ai.connect-timeout=2s",
                        "nerdnam.ai.read-timeout=30s",
                        "nerdnam.ai.read-timeouts.guide=120s",
                        "nerdnam.ai.read-timeouts.requirements-extract=20s",
                        "nerdnam.ai.read-timeouts.gap-guide=20s")
                .run(context -> {
                    AiServerProperties properties = context.getBean(AiServerProperties.class);

                    assertThat(properties.readTimeout(AiEndpoint.GUIDE)).isEqualTo(Duration.ofSeconds(120));
                    assertThat(properties.readTimeout(AiEndpoint.REQUIREMENTS_EXTRACT)).isEqualTo(Duration.ofSeconds(20));
                    assertThat(properties.readTimeout(AiEndpoint.GAP_GUIDE)).isEqualTo(Duration.ofSeconds(20));
                    assertThat(properties.readTimeout(AiEndpoint.MATCH)).isEqualTo(Duration.ofSeconds(30));   // 기본값
                });
    }

    @Configuration
    @EnableConfigurationProperties(AiServerProperties.class)
    static class Config {
    }
}
