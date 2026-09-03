package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.assignment.repository.AssignmentImageRepository;
import com.codegym.mathclass.bugreport.repository.BugReportImageRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.dto.SupabaseFileObject;
import com.codegym.mathclass.storage.dto.UpdateStorageCleanupConfigRequest;
import com.codegym.mathclass.storage.entity.StorageCleanupConfig;
import com.codegym.mathclass.storage.repository.StorageCleanupConfigRepository;
import com.codegym.mathclass.storage.scheduler.StorageCleanupScheduler;
import com.codegym.mathclass.storage.service.impl.StorageCleanupServiceImpl;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorageCleanupServiceImplTest {

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssignmentImageRepository assignmentImageRepository;

    @Mock
    private BugReportImageRepository bugReportImageRepository;

    @Mock
    private SystemLogService systemLogService;

    @Mock
    private StorageCleanupConfigRepository configRepository;

    @Mock
    private ObjectProvider<StorageCleanupScheduler> schedulerProvider;

    @Mock
    private StorageCleanupScheduler storageCleanupScheduler;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private StorageCleanupServiceImpl storageCleanupService;

    private StorageCleanupConfig mockConfig;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageCleanupService, "defaultEnabled", true);
        ReflectionTestUtils.setField(storageCleanupService, "defaultCronExpression", "0 0 3 * * SUN");
        ReflectionTestUtils.setField(storageCleanupService, "defaultGracePeriodHours", 24);

        mockConfig = StorageCleanupConfig.builder()
                .id(StorageCleanupConfig.DEFAULT_CONFIG_ID)
                .enabled(true)
                .cronExpression("0 0 3 * * SUN")
                .gracePeriodHours(24)
                .build();
    }

    @Nested
    @DisplayName("runCleanup Tests")
    class RunCleanupTests {

        @Test
        @DisplayName("Should detect orphan files older than grace period and delete them")
        void runCleanup_DeletesOrphanFilesOlderThan24h() {
            when(configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)).thenReturn(Optional.of(mockConfig));

            // DB has 1 active avatar and 1 active assignment image
            when(userRepository.findAllDistinctAvatarUrls())
                    .thenReturn(List.of("https://xyz.supabase.co/storage/v1/object/public/avatar/images/user1.png"));
            when(assignmentImageRepository.findAllDistinctImageUrls())
                    .thenReturn(List.of("https://xyz.supabase.co/storage/v1/object/public/assignment_image/images/assign1.png"));
            when(bugReportImageRepository.findAllDistinctImageUrls())
                    .thenReturn(Collections.emptyList());

            when(supabaseStorageService.extractObjectPath("https://xyz.supabase.co/storage/v1/object/public/avatar/images/user1.png"))
                    .thenReturn("images/user1.png");
            when(supabaseStorageService.extractObjectPath("https://xyz.supabase.co/storage/v1/object/public/assignment_image/images/assign1.png"))
                    .thenReturn("images/assign1.png");

            // Bucket avatar has: user1.png (in DB, old), orphan_old_avatar.png (not in DB, old: 48h ago), orphan_new_avatar.png (not in DB, fresh: 2h ago)
            Instant now = Instant.now();
            SupabaseFileObject av1 = SupabaseFileObject.builder().name("images/user1.png").createdAt(now.minus(48, ChronoUnit.HOURS).toString()).build();
            SupabaseFileObject av2OrphanOld = SupabaseFileObject.builder().name("images/orphan_old_avatar.png").createdAt(now.minus(48, ChronoUnit.HOURS).toString()).build();
            SupabaseFileObject av3OrphanNew = SupabaseFileObject.builder().name("images/orphan_new_avatar.png").createdAt(now.minus(2, ChronoUnit.HOURS).toString()).build();

            when(supabaseStorageService.listObjects(eq("avatar"), eq("images"), eq(100), eq(0)))
                    .thenReturn(List.of(av1, av2OrphanOld, av3OrphanNew));

            // Bucket assignment_image has: assign1.png (in DB, old), orphan_assign.png (not in DB, old: 72h ago)
            SupabaseFileObject as1 = SupabaseFileObject.builder().name("images/assign1.png").createdAt(now.minus(48, ChronoUnit.HOURS).toString()).build();
            SupabaseFileObject as2OrphanOld = SupabaseFileObject.builder().name("images/orphan_assign.png").createdAt(now.minus(72, ChronoUnit.HOURS).toString()).build();

            when(supabaseStorageService.listObjects(eq("assignment_image"), eq("images"), eq(100), eq(0)))
                    .thenReturn(List.of(as1, as2OrphanOld));

            when(supabaseStorageService.deleteImages(eq("avatar"), anyList())).thenReturn(1);
            when(supabaseStorageService.deleteImages(eq("assignment_image"), anyList())).thenReturn(1);

            StorageCleanupResponse response = storageCleanupService.runCleanup(24, false);

            assertThat(response).isNotNull();
            assertThat(response.getTotalFilesScanned()).isEqualTo(5);
            assertThat(response.getOrphanFilesDetected()).isEqualTo(2);
            assertThat(response.getFilesDeletedSuccessfully()).isEqualTo(2);
            assertThat(response.isDryRun()).isFalse();

            verify(supabaseStorageService).deleteImages("avatar", List.of("images/orphan_old_avatar.png"));
            verify(supabaseStorageService).deleteImages("assignment_image", List.of("images/orphan_assign.png"));
            verify(systemLogService).logInfo(eq("SYSTEM_STORAGE_GC"), eq("STORAGE_CLEANUP_EXECUTE"), eq("STORAGE"), anyString());
        }

        @Test
        @DisplayName("Should not delete any files when dryRun is true")
        void runCleanup_DryRun_DoesNotDeleteFiles() {
            when(configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)).thenReturn(Optional.of(mockConfig));
            when(userRepository.findAllDistinctAvatarUrls()).thenReturn(Collections.emptyList());
            when(assignmentImageRepository.findAllDistinctImageUrls()).thenReturn(Collections.emptyList());
            when(bugReportImageRepository.findAllDistinctImageUrls()).thenReturn(Collections.emptyList());

            Instant oldTime = Instant.now().minus(48, ChronoUnit.HOURS);
            SupabaseFileObject orphan = SupabaseFileObject.builder().name("images/orphan.png").createdAt(oldTime.toString()).build();

            when(supabaseStorageService.listObjects(eq("avatar"), eq("images"), eq(100), eq(0)))
                    .thenReturn(List.of(orphan));
            when(supabaseStorageService.listObjects(eq("assignment_image"), eq("images"), eq(100), eq(0)))
                    .thenReturn(Collections.emptyList());

            StorageCleanupRequest request = StorageCleanupRequest.builder()
                    .gracePeriodHours(24)
                    .dryRun(true)
                    .build();

            StorageCleanupResponse response = storageCleanupService.runCleanup(request);

            assertThat(response.isDryRun()).isTrue();
            assertThat(response.getOrphanFilesDetected()).isEqualTo(1);
            assertThat(response.getFilesDeletedSuccessfully()).isEqualTo(0);

            verify(supabaseStorageService, never()).deleteImages(anyString(), anyList());
        }
    }

    @Nested
    @DisplayName("getCleanupStatus and updateConfig Tests")
    class ConfigTests {

        @Test
        @DisplayName("Should return status matching database configuration")
        void getCleanupStatus_Success() {
            when(configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)).thenReturn(Optional.of(mockConfig));

            StorageCleanupStatusResponse status = storageCleanupService.getCleanupStatus();

            assertThat(status).isNotNull();
            assertThat(status.isEnabled()).isTrue();
            assertThat(status.getCronExpression()).isEqualTo("0 0 3 * * SUN");
            assertThat(status.getGracePeriodHours()).isEqualTo(24);
        }

        @Test
        @DisplayName("Should update configuration and trigger dynamic reschedule")
        void updateConfig_Success() {
            when(configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)).thenReturn(Optional.of(mockConfig));
            when(configRepository.save(any(StorageCleanupConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(schedulerProvider.getIfAvailable()).thenReturn(storageCleanupScheduler);

            UpdateStorageCleanupConfigRequest request = UpdateStorageCleanupConfigRequest.builder()
                    .enabled(false)
                    .cronExpression("0 0 2 * * *")
                    .gracePeriodHours(48)
                    .build();

            StorageCleanupStatusResponse response = storageCleanupService.updateConfig(request);

            assertThat(response).isNotNull();
            assertThat(response.isEnabled()).isFalse();
            assertThat(response.getCronExpression()).isEqualTo("0 0 2 * * *");
            assertThat(response.getGracePeriodHours()).isEqualTo(48);

            verify(configRepository).save(mockConfig);
            verify(storageCleanupScheduler).reschedule(mockConfig);
        }

        @Test
        @DisplayName("Should throw BadRequestException when cron expression is invalid")
        void updateConfig_InvalidCron_ThrowsBadRequestException() {
            UpdateStorageCleanupConfigRequest request = UpdateStorageCleanupConfigRequest.builder()
                    .enabled(true)
                    .cronExpression("invalid-cron-format")
                    .gracePeriodHours(24)
                    .build();

            assertThatThrownBy(() -> storageCleanupService.updateConfig(request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Biểu thức Cron không hợp lệ");

            verify(configRepository, never()).save(any());
        }
    }
}
