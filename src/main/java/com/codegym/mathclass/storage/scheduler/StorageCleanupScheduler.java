package com.codegym.mathclass.storage.scheduler;

import com.codegym.mathclass.storage.entity.StorageCleanupConfig;
import com.codegym.mathclass.storage.repository.StorageCleanupConfigRepository;
import com.codegym.mathclass.storage.service.StorageCleanupService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledFuture;

import com.codegym.mathclass.common.lock.DistributedLockService;

@Component
@Slf4j
public class StorageCleanupScheduler {

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<StorageCleanupService> storageCleanupServiceProvider;
    private final StorageCleanupConfigRepository configRepository;
    private final DistributedLockService distributedLockService;

    private static final String STORAGE_CLEANUP_LOCK = "lock:cron:storage-cleanup";

    public StorageCleanupScheduler(
            @Qualifier("storageTaskScheduler") TaskScheduler taskScheduler,
            ObjectProvider<StorageCleanupService> storageCleanupServiceProvider,
            StorageCleanupConfigRepository configRepository,
            DistributedLockService distributedLockService
    ) {
        this.taskScheduler = taskScheduler;
        this.storageCleanupServiceProvider = storageCleanupServiceProvider;
        this.configRepository = configRepository;
        this.distributedLockService = distributedLockService;
    }

    @Value("${app.storage.cleanup.enabled:false}")
    private boolean defaultEnabled;

    @Value("${app.storage.cleanup.cron:0 0 3 * * SUN}")
    private String defaultCron;

    @Value("${app.storage.cleanup.grace-period-hours:24}")
    private int defaultGracePeriod;

    private ScheduledFuture<?> scheduledFuture;

    @PostConstruct
    public void init() {
        StorageCleanupConfig config = configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)
                .orElseGet(this::createInitialConfig);
        reschedule(config);
    }

    private StorageCleanupConfig createInitialConfig() {
        StorageCleanupConfig config = StorageCleanupConfig.builder()
                .id(StorageCleanupConfig.DEFAULT_CONFIG_ID)
                .enabled(defaultEnabled)
                .cronExpression(defaultCron)
                .gracePeriodHours(defaultGracePeriod)
                .build();
        return configRepository.save(config);
    }

    public synchronized void reschedule(StorageCleanupConfig config) {
        if (scheduledFuture != null && !scheduledFuture.isCancelled()) {
            scheduledFuture.cancel(false);
            log.info("[Storage Scheduler] Cancelled previous storage cleanup schedule.");
        }

        if (config == null || !config.isEnabled()) {
            log.info("[Storage Scheduler] Storage cleanup automatic schedule is DISABLED.");
            return;
        }

        String cron = config.getCronExpression();
        if (cron == null || !CronExpression.isValidExpression(cron)) {
            log.error("[Storage Scheduler] Invalid cron expression: '{}'. Scheduling aborted.", cron);
            return;
        }

        try {
            scheduledFuture = taskScheduler.schedule(
                    this::executeScheduledCleanup,
                    new CronTrigger(cron)
            );
            log.info("[Storage Scheduler] Successfully scheduled dynamic storage cleanup with cron: '{}', gracePeriod: {}h",
                    cron, config.getGracePeriodHours());
        } catch (Exception e) {
            log.error("[Storage Scheduler] Failed to schedule task with cron '{}': {}", cron, e.getMessage(), e);
        }
    }

    public void executeScheduledCleanup() {
        distributedLockService.tryRunWithLock(STORAGE_CLEANUP_LOCK, 0, 1800, () -> {
            log.info("[Storage Scheduler] Triggering automatic scheduled storage cleanup task...");
            try {
                StorageCleanupService service = storageCleanupServiceProvider.getIfAvailable();
                if (service != null) {
                    StorageCleanupConfig config = configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID).orElse(null);
                    int gracePeriod = config != null ? config.getGracePeriodHours() : defaultGracePeriod;
                    service.runCleanup(gracePeriod, false);
                }
            } catch (Exception e) {
                log.error("[Storage Scheduler] Error executing scheduled storage cleanup: {}", e.getMessage(), e);
            }
        });
    }
}
