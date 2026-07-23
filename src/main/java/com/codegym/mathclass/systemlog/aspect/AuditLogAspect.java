package com.codegym.mathclass.systemlog.aspect;

import com.codegym.mathclass.systemlog.annotation.AuditLog;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogAspect {

    private final SystemLogService systemLogService;

    @Around("@annotation(auditLog)")
    public Object logExecution(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        String actor = getCurrentUserEmail();
        String ipAddress = getClientIpAddress();
        String userAgent = getUserAgent();
        String action = auditLog.action();
        String resourceType = auditLog.resourceType();

        Object result;
        try {
            result = joinPoint.proceed();
            systemLogService.log(actor, action, auditLog.level(), resourceType, null, ipAddress, userAgent, "SUCCESS");
            return result;
        } catch (Throwable throwable) {
            systemLogService.log(actor, action + " (Thất bại: " + throwable.getMessage() + ")", auditLog.level(), resourceType, null, ipAddress, userAgent, "FAILED");
            throw throwable;
        }
    }

    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "System";
    }

    private String getClientIpAddress() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return null;
    }

    private String getUserAgent() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String agent = request.getHeader("User-Agent");
            if (agent != null && agent.length() > 255) {
                return agent.substring(0, 255);
            }
            return agent;
        }
        return null;
    }
}
