package com.codegym.mathclass.storage.scheduler;

import com.codegym.mathclass.storage.entity.StorageCleanupConfig;
import com.codegym.mathclass.storage.repository.StorageCleanupConfigRepository;
import com.codegym.mathclass.storage.service.StorageCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.concurrent.ScheduledFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageCleanupSchedulerTest {

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private ObjectProvider<StorageCleanupService> storageCleanupServiceProvider;

    @Mock
    private StorageCleanupService storageCleanupService;

    @Mock
    private StorageCleanupConfigRepository configRepository;

    @Mock
    private ScheduledFuture scheduledFuture;

    @InjectMocks
    private StorageCleanupScheduler storageCleanupScheduler;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageCleanupScheduler, "defaultEnabled", true);
        ReflectionTestUtils.setField(storageCleanupScheduler, "defaultCron", "0 0 3 * * SUN");
        ReflectionTestUtils.setField(storageCleanupScheduler, "defaultGracePeriod", 24);
    }

    @Test
    @DisplayName("init should load config from DB and schedule task")
    void init_Success() {
        StorageCleanupConfig config = StorageCleanupConfig.builder()
                .id(1L)
                .enabled(true)
                .cronExpression("0 0 3 * * SUN")
                .gracePeriodHours(24)
                .build();

        when(configRepository.findById(1L)).thenReturn(Optional.of(config));
        doReturn(scheduledFuture).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        storageCleanupScheduler.init();

        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("reschedule should cancel previous task and schedule new when enabled")
    void reschedule_Enabled_SchedulesNewTask() {
        ReflectionTestUtils.setField(storageCleanupScheduler, "scheduledFuture", scheduledFuture);

        StorageCleanupConfig config = StorageCleanupConfig.builder()
                .id(1L)
                .enabled(true)
                .cronExpression("0 0 2 * * *")
                .gracePeriodHours(12)
                .build();

        doReturn(mock(ScheduledFuture.class)).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        storageCleanupScheduler.reschedule(config);

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("reschedule should cancel previous task and not schedule when disabled")
    void reschedule_Disabled_CancelsAndDoesNotSchedule() {
        ReflectionTestUtils.setField(storageCleanupScheduler, "scheduledFuture", scheduledFuture);

        StorageCleanupConfig config = StorageCleanupConfig.builder()
                .id(1L)
                .enabled(false)
                .cronExpression("0 0 2 * * *")
                .gracePeriodHours(12)
                .build();

        storageCleanupScheduler.reschedule(config);

        verify(scheduledFuture).cancel(false);
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("executeScheduledCleanup should call service runCleanup")
    void executeScheduledCleanup_Success() {
        when(storageCleanupServiceProvider.getIfAvailable()).thenReturn(storageCleanupService);
        when(configRepository.findById(1L)).thenReturn(Optional.of(
                StorageCleanupConfig.builder().id(1L).gracePeriodHours(48).build()
        ));

        storageCleanupScheduler.executeScheduledCleanup();

        verify(storageCleanupService).runCleanup(48, false);
    }
}
