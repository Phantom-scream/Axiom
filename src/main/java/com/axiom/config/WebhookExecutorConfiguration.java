package com.axiom.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WebhookExecutorConfiguration {
    @Bean("webhookExecutor")
    Executor webhookExecutor(WebhookProperties properties,
            @org.springframework.beans.factory.annotation.Value("${spring.datasource.hikari.maximum-pool-size:10}") int poolSize) {
        if (properties.enabledValue() && poolSize < properties.effectiveMaxThreads()*2+2)
            throw new IllegalStateException("Database pool capacity must be at least twice webhook maximum threads plus two for durable claims and analysis.");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.effectiveCoreThreads());
        executor.setMaxPoolSize(properties.effectiveMaxThreads());
        executor.setQueueCapacity(properties.effectiveQueueCapacity());
        executor.setThreadNamePrefix("axiom-webhook-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
