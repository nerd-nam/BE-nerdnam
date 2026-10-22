package com.nerdnam.infra.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** nerdnam.ai.stub 값에 따라 실제 클라이언트나 스텁 중 하나를 빈으로 등록한다 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiServerClient aiServerClient(AiServerProperties properties) {
        return properties.stub() ? new StubAiServerClient() : HttpAiServerClient.create(properties);
    }
}
