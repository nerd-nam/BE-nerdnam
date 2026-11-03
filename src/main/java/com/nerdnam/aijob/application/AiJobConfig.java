package com.nerdnam.aijob.application;

import com.nerdnam.infra.ai.AiQueueProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableScheduling
public class AiJobConfig {

    /**
     * AI 작업 전용 스레드 풀. 웹 요청 스레드는 작업을 접수만 하고 바로 돌아간다.
     *
     * <p>AI 호출을 큐로 보내면(ADR-0009) 작업 스레드는 결과를 기다리기만 하므로 <b>가상 스레드</b>로 만들고 수를
     * 늘린다({@code queue-threads}). 스레드 풀로 두는 이유는 동시 작업 수 상한과 큐 지표(BE #44)를 그대로 쓰기 위해서다.
     */
    @Bean
    public TaskExecutor aiJobExecutor(AiJobProperties properties, AiQueueProperties queue) {
        if (properties.sync()) {
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int threads = queue.enabled() ? properties.queueThreads() : properties.threads();
        if (queue.enabled()) {
            executor.setThreadFactory(Thread.ofVirtual().name("ai-job-", 1).factory());
        } else {
            executor.setThreadNamePrefix("ai-job-");
        }
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(200);
        // 접수 요청의 trace 를 작업 스레드로 넘긴다 (BE #51). 없으면 작업 span 이 고아가 되어
        // "웹 요청 → BE → AI → Bedrock" 이 한 trace 로 이어지지 않는다
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
