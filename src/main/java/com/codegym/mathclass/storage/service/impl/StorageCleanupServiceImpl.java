package com.codegym.mathclass.storage.service.impl;

import com.codegym.mathclass.assignment.repository.AssignmentImageRepository;
import com.codegym.mathclass.bugreport.repository.BugReportImageRepository;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.request.StorageCleanupRequest;
import com.codegym.mathclass.storage.dto.response.StorageCleanupResponse;
import com.codegym.mathclass.storage.dto.response.StorageCleanupStatusResponse;
import com.codegym.mathclass.storage.dto.SupabaseFileObject;
import com.codegym.mathclass.storage.dto.request.UpdateStorageCleanupConfigRequest;
import com.codegym.mathclass.storage.entity.StorageCleanupConfig;
import com.codegym.mathclass.storage.repository.StorageCleanupConfigRepository;
import com.codegym.mathclass.storage.scheduler.StorageCleanupScheduler;
import com.codegym.mathclass.storage.service.StorageCleanupService;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.SupabaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageCleanupServiceImpl implements StorageCleanupService {

    private final SupabaseStorageService supabaseStorageService;
    private final UserRepository userRepository;
    private final AssignmentImageRepository assignmentImageRepository;
    private final BugReportImageRepository bugReportImageRepository;
    private final SystemLogService systemLogService;
    private final StorageCleanupConfigRepository configRepository;
    private final ObjectProvider<StorageCleanupScheduler> schedulerProvider;
    private final ObjectMapper objectMapper;

    @Value("${app.storage.cleanup.enabled:false}")
    private boolean defaultEnabled;

    @Value("${app.storage.cleanup.cron:0 0 3 * * SUN}")
    private String defaultCronExpression;

    @Value("${app.storage.cleanup.grace-period-hours:24}")
    private int defaultGracePeriodHours;

    private static final List<String> MANAGED_BUCKETS = List.of("avatar", "assignment_image");

    @Transactional(readOnly = true)
    private StorageCleanupConfig getOrCreateConfig() {
        return configRepository.findById(StorageCleanupConfig.DEFAULT_CONFIG_ID)
                .orElseGet(() -> {
                    StorageCleanupConfig config = StorageCleanupConfig.builder()
                            .id(StorageCleanupConfig.DEFAULT_CONFIG_ID)
                            .enabled(defaultEnabled)
                            .cronExpression(defaultCronExpression)
                            .gracePeriodHours(defaultGracePeriodHours)
                            .build();
                    return configRepository.save(config);
                });
    }

    @Override
    public StorageCleanupResponse runCleanup(StorageCleanupRequest request) {
        StorageCleanupConfig config = getOrCreateConfig();
        int gracePeriod = request != null && request.getGracePeriodHours() != null
                ? request.getGracePeriodHours()
                : config.getGracePeriodHours();
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        return runCleanup(gracePeriod, dryRun);
    }

    @Override
    public StorageCleanupResponse runCleanup(int gracePeriodHours, boolean dryRun) {
        long startTime = System.currentTimeMillis();
        Instant cutoffInstant = Instant.now().minus(gracePeriodHours, ChronoUnit.HOURS);

        log.info("[Storage GC] Starting storage cleanup: gracePeriodHours={}, dryRun={}, cutoff={}",
                gracePeriodHours, dryRun, cutoffInstant);

        // 1. Thu thập toàn bộ active URLs từ CSDL
        Set<String> activeAvatarUrls = new HashSet<>(userRepository.findAllDistinctAvatarUrls());
        Set<String> activeAssignmentUrls = new HashSet<>(assignmentImageRepository.findAllDistinctImageUrls());
        Set<String> activeBugReportUrls = new HashSet<>(bugReportImageRepository.findAllDistinctImageUrls());

        Set<String> activeAvatarPaths = extractNormalizedPaths(activeAvatarUrls);
        Set<String> activeAssignmentAndBugPaths = new HashSet<>();
        activeAssignmentAndBugPaths.addAll(extractNormalizedPaths(activeAssignmentUrls));
        activeAssignmentAndBugPaths.addAll(extractNormalizedPaths(activeBugReportUrls));

        log.info("[Storage GC] Active paths in DB: avatar={}, assignment_image/bug={}",
                activeAvatarPaths.size(), activeAssignmentAndBugPaths.size());

        int totalFilesScanned = 0;
        int totalOrphansDetected = 0;
        int totalDeletedSuccessfully = 0;
        int totalFailedDeletions = 0;

        // 2. Quét từng bucket trên Supabase Storage
        for (String bucket : MANAGED_BUCKETS) {
            Set<String> activePathsForBucket = "avatar".equalsIgnoreCase(bucket)
                    ? activeAvatarPaths
                    : activeAssignmentAndBugPaths;

            List<String> orphanPathsInBucket = new ArrayList<>();
            int offset = 0;
            int limit = 100;

            while (true) {
                List<SupabaseFileObject> files = supabaseStorageService.listObjects(bucket, "images", limit, offset);
                if (files == null || files.isEmpty()) {
                    break;
                }

                totalFilesScanned += files.size();

                for (SupabaseFileObject file : files) {
                    if (file == null || file.getName() == null || file.getName().isBlank()) {
                        continue;
                    }

                    // Không dọn dẹp placeholder folder
                    if (file.getName().endsWith(".emptyFolderPlaceholder")) {
                        continue;
                    }

                    Instant createdAt = file.getCreatedInstant();
                    // Chỉ xét các file tạo trước thời điểm cutoff (Grace Period)
                    if (createdAt == null || createdAt.isBefore(cutoffInstant)) {
                        String rawName = file.getName();
                        String normalizedPath = rawName.startsWith("images/") ? rawName : "images/" + rawName;

                        boolean isUsed = activePathsForBucket.contains(rawName)
                                || activePathsForBucket.contains(normalizedPath);

                        if (!isUsed) {
                            orphanPathsInBucket.add(normalizedPath);
                            totalOrphansDetected++;
                        }
                    }
                }

                if (files.size() < limit) {
                    break;
                }
                offset += files.size();
            }

            log.info("[Storage GC] Bucket {}: scanned, found {} orphan files older than cutoff",
                    bucket, orphanPathsInBucket.size());

            // 3. Xóa các file mồ côi nếu không phải chế độ dryRun
            if (!dryRun && !orphanPathsInBucket.isEmpty()) {
                int deleted = supabaseStorageService.deleteImages(bucket, orphanPathsInBucket);
                totalDeletedSuccessfully += deleted;
                if (deleted < orphanPathsInBucket.size()) {
                    totalFailedDeletions += (orphanPathsInBucket.size() - deleted);
                }
            }
        }

        long executionTimeMs = System.currentTimeMillis() - startTime;
        LocalDateTime now = LocalDateTime.now();

        StorageCleanupResponse response = StorageCleanupResponse.builder()
                .scannedBuckets(MANAGED_BUCKETS)
                .totalFilesScanned(totalFilesScanned)
                .orphanFilesDetected(totalOrphansDetected)
                .filesDeletedSuccessfully(totalDeletedSuccessfully)
                .failedDeletions(totalFailedDeletions)
                .executionTimeMs(executionTimeMs)
                .dryRun(dryRun)
                .completedAt(now)
                .build();

        // 4. Cập nhật kết quả vào CSDL
        try {
            StorageCleanupConfig config = getOrCreateConfig();
            config.setLastRunAt(now);
            config.setLastRunResultJson(objectMapper.writeValueAsString(response));
            configRepository.save(config);
        } catch (Exception e) {
            log.warn("[Storage GC] Failed to persist cleanup result in DB: {}", e.getMessage());
        }

        // 5. Ghi nhật ký hệ thống (Chỉ ghi khi dọn dẹp thực tế, không ghi cho lần quét thử nghiệm dryRun)
        if (!dryRun) {
            try {
                String actor = getCurrentActor();
                double durationSec = executionTimeMs / 1000.0;
                String logAction = String.format("Dọn dẹp bộ nhớ: Đã quét %d tệp, thu hồi thành công %d ảnh rác (Thời gian: %.1fs)",
                        totalFilesScanned, totalDeletedSuccessfully, durationSec);

                systemLogService.logInfo(actor, logAction, "STORAGE", "SUPABASE_STORAGE");
            } catch (Exception e) {
                log.warn("[Storage GC] Failed to log system audit event: {}", e.getMessage());
            }
        }

        log.info("[Storage GC] Finished cleanup: scanned={}, orphans={}, deleted={}, duration={}ms",
                totalFilesScanned, totalOrphansDetected, totalDeletedSuccessfully, executionTimeMs);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public StorageCleanupStatusResponse getCleanupStatus() {
        StorageCleanupConfig config = getOrCreateConfig();
        StorageCleanupResponse lastResult = null;
        if (config.getLastRunResultJson() != null && !config.getLastRunResultJson().isBlank()) {
            try {
                lastResult = objectMapper.readValue(config.getLastRunResultJson(), StorageCleanupResponse.class);
            } catch (Exception e) {
                log.warn("[Storage GC] Failed to deserialize lastRunResultJson: {}", e.getMessage());
            }
        }

        return StorageCleanupStatusResponse.builder()
                .enabled(config.isEnabled())
                .cronExpression(config.getCronExpression())
                .gracePeriodHours(config.getGracePeriodHours())
                .lastRunAt(config.getLastRunAt())
                .lastRunResult(lastResult)
                .build();
    }

    @Override
    @Transactional
    public StorageCleanupStatusResponse updateConfig(UpdateStorageCleanupConfigRequest request) {
        if (request == null) {
            throw new BadRequestException("Dữ liệu cấu hình không hợp lệ");
        }

        String cron = request.getCronExpression() != null ? request.getCronExpression().trim() : "";
        if (!CronExpression.isValidExpression(cron)) {
            throw new BadRequestException("Biểu thức Cron không hợp lệ: '" + cron + "'");
        }

        StorageCleanupConfig config = getOrCreateConfig();
        config.setEnabled(Boolean.TRUE.equals(request.getEnabled()));
        config.setCronExpression(cron);
        config.setGracePeriodHours(request.getGracePeriodHours());

        StorageCleanupConfig savedConfig = configRepository.save(config);

        // Tái lập lịch tự động ngay lập tức
        StorageCleanupScheduler scheduler = schedulerProvider.getIfAvailable();
        if (scheduler != null) {
            scheduler.reschedule(savedConfig);
        }

        // Ghi nhật ký kiểm toán hệ thống
        try {
            String actor = getCurrentActor();
            String logAction = String.format("Cập nhật cấu hình dọn dẹp bộ nhớ: %s, chu kỳ '%s', thời gian chờ %dh",
                    savedConfig.isEnabled() ? "Bật" : "Tắt",
                    savedConfig.getCronExpression(),
                    savedConfig.getGracePeriodHours());
            systemLogService.logInfo(actor, logAction, "STORAGE", "STORAGE_CONFIG");
        } catch (Exception e) {
            log.warn("[Storage GC] Failed to log config update audit: {}", e.getMessage());
        }

        log.info("[Storage GC] Updated storage cleanup config: enabled={}, cron='{}', gracePeriod={}h",
                savedConfig.isEnabled(), savedConfig.getCronExpression(), savedConfig.getGracePeriodHours());

        return getCleanupStatus();
    }

    private String getCurrentActor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception e) {
            log.debug("Cannot resolve current user in security context: {}", e.getMessage());
        }
        return "Hệ thống";
    }

    private Set<String> extractNormalizedPaths(Set<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return Collections.emptySet();
        }

        return urls.stream()
                .filter(Objects::nonNull)
                .map(supabaseStorageService::extractObjectPath)
                .filter(Objects::nonNull)
                .map(path -> path.startsWith("images/") ? path : "images/" + path)
                .collect(Collectors.toSet());
    }
}
