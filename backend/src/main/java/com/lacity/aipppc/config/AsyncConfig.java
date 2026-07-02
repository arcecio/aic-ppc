package com.lacity.aipppc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Thread pool backing {@code @Async} pre-plan-check screening runs. Screening is
 * long-running (document parsing + rule engine + optional AI call) so it executes
 * off the request thread, which lets the intake API return immediately with a
 * PENDING run the client polls — the same pattern the integration API exposes to
 * external City systems via webhooks (Appendix 3 §2.1.4).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "screeningExecutor")
    public TaskExecutor screeningExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("screening-");
        executor.initialize();
        return executor;
    }
}
