package com.codegym.mathclass.systemlog.service;

import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface SystemLogService {
    Page<SystemLogResponse> getLogs(SystemLogLevel level, String actor, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    void logInfo(String actor, String action, Long userId);
    void logWarning(String actor, String action, Long userId);
    void logError(String actor, String action, Long userId);
}
