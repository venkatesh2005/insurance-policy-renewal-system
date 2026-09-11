package com.fulcrumdigital.policyrenewal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class ExecutorConfig {

    // Thread pool for the NotificationPoller workers — bounded so
    // notification sending never spawns unlimited threads.
    @Bean
    public ThreadPoolTaskExecutor notificationExecutor(
            @Value("${notification.pool.core-size:3}") int coreSize,
            @Value("${notification.pool.max-size:5}") int maxSize,
            @Value("${notification.pool.queue-capacity:50}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notif-worker-");
        executor.initialize();
        return executor;
    }

    // Scheduler thread pool used by BatchJobScheduler for dynamic
    // rescheduling (the reschedule() method after a DB cron update).
    // Separate from the notification pool — keeps job scheduling
    // and notification processing on independent thread pools.
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5);
        scheduler.setThreadNamePrefix("job-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
