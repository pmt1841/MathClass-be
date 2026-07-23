package com.codegym.mathclass.systemlog.scheduler;

import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class LogRetentionScheduler {

    private final SystemLogRepository systemLogRepository;

    @Value("${app.audit-log.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(retentionDays);
        log.info("Chạy dọn dẹp nhật ký hệ thống cũ trước ngày: {}", cutoffDate);
        systemLogRepository.deleteByCreatedAtBefore(cutoffDate);
    }
}
