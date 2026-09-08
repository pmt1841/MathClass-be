package com.codegym.mathclass.auth.audit;

import com.codegym.mathclass.auth.dto.request.AuthType;
import com.codegym.mathclass.auth.entity.AuthAuditLog;
import com.codegym.mathclass.auth.repository.AuthAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthAuditLogger {

    private final AuthAuditLogRepository authAuditLogRepository;

    @Async
    public void logSuccess(Long userId, String email, AuthType authType, HttpServletRequest request) {
        try {
            AuthAuditLog auditLog = AuthAuditLog.builder()
                    .userId(userId)
                    .email(email)
                    .authType(authType)
                    .status("SUCCESS")
                    .clientIp(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            authAuditLogRepository.save(auditLog);
            log.info("Audit Log [SUCCESS]: user={}, authType={}, ip={}", email, authType, auditLog.getClientIp());
        } catch (Exception e) {
            log.error("Lỗi khi ghi Audit Log [SUCCESS]: {}", e.getMessage(), e);
        }
    }

    @Async
    public void logFailure(String email, AuthType authType, String failureReason, HttpServletRequest request) {
        try {
            AuthAuditLog auditLog = AuthAuditLog.builder()
                    .email(email != null ? email : "UNKNOWN")
                    .authType(authType)
                    .status("FAILED")
                    .failureReason(failureReason)
                    .clientIp(extractClientIp(request))
                    .userAgent(extractUserAgent(request))
                    .build();
            authAuditLogRepository.save(auditLog);
            log.warn("Audit Log [FAILED]: user={}, authType={}, reason={}, ip={}", email, authType, failureReason, auditLog.getClientIp());
        } catch (Exception e) {
            log.error("Lỗi khi ghi Audit Log [FAILED]: {}", e.getMessage(), e);
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    public String extractUserAgent(HttpServletRequest request) {
        if (request == null) {
            return "UNKNOWN";
        }
        String userAgent = request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent) && userAgent.length() > 500) {
            return userAgent.substring(0, 500);
        }
        return userAgent;
    }
}
