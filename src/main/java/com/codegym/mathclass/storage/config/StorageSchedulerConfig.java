package com.codegym.mathclass.storage.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class StorageSchedulerConfig {

    @Bean(name = "storageTaskScheduler")
    public ThreadPoolTaskScheduler storageTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("storage-gc-");
        scheduler.initialize();
        return scheduler;
    }
}
