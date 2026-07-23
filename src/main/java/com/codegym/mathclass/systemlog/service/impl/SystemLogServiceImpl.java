package com.codegym.mathclass.systemlog.service.impl;

import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLog;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.repository.SystemLogRepository;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SystemLogServiceImpl implements SystemLogService {

    private final SystemLogRepository systemLogRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<SystemLogResponse> getLogs(SystemLogLevel level, String resourceType, String actor, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Specification<SystemLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (level != null) {
                predicates.add(cb.equal(root.get("level"), level));
            }

            if (resourceType != null && !resourceType.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType.trim()));
            }

            if (actor != null && !actor.trim().isEmpty()) {
                String sanitizedActor = actor.trim()
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("actor")), "%" + sanitizedActor.toLowerCase() + "%"));
            }

            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
            }

            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return systemLogRepository.findAll(spec, pageable)
                .map(log -> SystemLogResponse.builder()
                        .id(log.getId())
                        .timestamp(log.getCreatedAt())
                        .actor(log.getActor())
                        .action(log.getAction())
                        .level(log.getLevel())
                        .resourceType(log.getResourceType())
                        .resourceId(log.getResourceId())
                        .ipAddress(log.getIpAddress())
                        .userAgent(log.getUserAgent())
                        .status(log.getStatus())
                        .build());
    }

    @Override
    @Transactional
    public void log(String actor, String action, SystemLogLevel level, String resourceType, String resourceId, String ipAddress, String userAgent, String status) {
        SystemLog log = SystemLog.builder()
                .actor(actor != null ? actor : "System")
                .action(action)
                .level(level != null ? level : SystemLogLevel.INFO)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status(status != null ? status : "SUCCESS")
                .build();
        systemLogRepository.save(log);
    }

    @Override
    @Transactional
    public void logInfo(String actor, String action, Long userId) {
        log(actor, action, SystemLogLevel.INFO, "USER", userId != null ? String.valueOf(userId) : null, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void logInfo(String actor, String action, String resourceType, String resourceId) {
        log(actor, action, SystemLogLevel.INFO, resourceType, resourceId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void logWarning(String actor, String action, Long userId) {
        log(actor, action, SystemLogLevel.WARNING, "USER", userId != null ? String.valueOf(userId) : null, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void logWarning(String actor, String action, String resourceType, String resourceId) {
        log(actor, action, SystemLogLevel.WARNING, resourceType, resourceId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public void logError(String actor, String action, Long userId) {
        log(actor, action, SystemLogLevel.ERROR, "USER", userId != null ? String.valueOf(userId) : null, null, null, "FAILED");
    }

    @Override
    @Transactional
    public void logError(String actor, String action, String resourceType, String resourceId) {
        log(actor, action, SystemLogLevel.ERROR, resourceType, resourceId, null, null, "FAILED");
    }
}
