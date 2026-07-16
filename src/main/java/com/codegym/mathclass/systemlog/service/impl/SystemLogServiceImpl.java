package com.codegym.mathclass.systemlog.service.impl;

import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLog;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private final SystemLogRepository systemLogRepository;

    @Override
    public Page<SystemLogResponse> getLogs(SystemLogLevel level, String actor, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        return systemLogRepository.findByFilters(level, actor, startDate, endDate, pageable)
                .map(log -> SystemLogResponse.builder()
                        .id(log.getId())
                        .timestamp(log.getTimestamp())
                        .actor(log.getActor())
                        .action(log.getAction())
                        .level(log.getLevel())
                        .build());
    }

    @Override
    public void logInfo(String actor, String action, Long userId) {
        saveLog(actor, action, SystemLogLevel.INFO, userId);
    }

    @Override
    public void logWarning(String actor, String action, Long userId) {
        saveLog(actor, action, SystemLogLevel.WARNING, userId);
    }

    @Override
    public void logError(String actor, String action, Long userId) {
        saveLog(actor, action, SystemLogLevel.ERROR, userId);
    }

    private void saveLog(String actor, String action, SystemLogLevel level, Long userId) {
        SystemLog log = SystemLog.builder()
                .timestamp(LocalDateTime.now())
                .actor(actor)
                .action(action)
                .level(level)
                .userId(userId)
                .build();
        systemLogRepository.save(log);
    }
}
