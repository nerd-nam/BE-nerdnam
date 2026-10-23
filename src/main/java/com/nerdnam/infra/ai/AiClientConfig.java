package com.nerdnam.infra.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/** nerdnam.ai.stub 값에 따라 실제 클라이언트나 스텁 중 하나를 고르고, 호출 기록 감싸기를 씌워 빈으로 등록한다 */
@Configuration
public class AiClientConfig {

    @Bean
    public AiServerClient aiServerClient(AiServerProperties properties, JdbcClient jdbc) {
        AiServerClient client = properties.stub() ? new StubAiServerClient() : HttpAiServerClient.create(properties);
        return new RecordingAiServerClient(client, new JdbcAiCallLog(jdbc));
    }
}
