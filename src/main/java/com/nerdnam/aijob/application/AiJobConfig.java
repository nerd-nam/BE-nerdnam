package com.nerdnam.aijob.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class AiJobConfig {

    /** AI 작업 전용 스레드 풀. 웹 요청 스레드는 작업을 접수만 하고 바로 돌아간다 */
    @Bean
    public TaskExecutor aiJobExecutor(AiJobProperties properties) {
        if (properties.sync()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-job-");
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(properties.threads());
        executor.setQueueCapacity(200);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
