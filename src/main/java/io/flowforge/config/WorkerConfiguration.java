package io.flowforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WorkerConfiguration {
    @Bean("workerTaskExecutor")
    public ThreadPoolTaskExecutor workerTaskExecutor(
            @Value("${flowforge.worker.concurrency:4}") int concurrency
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("flowforge-worker-");
        executor.initialize();
        return executor;
    }
}
