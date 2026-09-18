package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.BatchCreditAdjustRequest;
import com.codegym.mathclass.aiconfig.credit.dto.request.CreditAdjustRequest;
import com.codegym.mathclass.aiconfig.credit.dto.response.BatchCreditAdjustResponse;
import com.codegym.mathclass.aiconfig.credit.dto.response.CreditTransactionResponse;
import com.codegym.mathclass.aiconfig.credit.entity.CreditTransactionType;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag(name = "Admin - Credit Adjust", description = "APIs quản trị viên: điều chỉnh credit thủ công và xem sổ cái giao dịch")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/credits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminCreditAdjustController {

    private final AiCreditService aiCreditService;
    private final SystemLogService systemLogService;

    @Operation(summary = "Điều chỉnh credit thủ công (grant / hoàn tiền)")
    @PostMapping("/adjust")
    public ResponseEntity<Map<String, String>> adjust(
            @Valid @RequestBody CreditAdjustRequest request,
            HttpServletRequest httpRequest) {
        aiCreditService.adjustByAdmin(request.getUserId(), request.getAmount(), request.getReason());

        try {
            String actor = getActor();
            String ip = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String action = String.format("Điều chỉnh Credit người dùng #%d: %s%d điểm (Lý do: %s)",
                    request.getUserId(),
                    request.getAmount() >= 0 ? "+" : "",
                    request.getAmount(),
                    (request.getReason() != null && !request.getReason().isBlank()) ? request.getReason() : "Không có lý do");
            systemLogService.log(actor, action, SystemLogLevel.WARNING, "CREDIT", String.valueOf(request.getUserId()), ip, userAgent, "SUCCESS");
        } catch (Exception e) {
            log.warn("Lỗi khi ghi nhật ký điều chỉnh credit: {}", e.getMessage());
        }

        return ResponseEntity.ok(Map.of("message", "Điều chỉnh credit thành công"));
    }

    @Operation(summary = "Điều chỉnh credit hàng loạt cho nhiều người dùng")
    @PostMapping("/adjust-batch")
    public ResponseEntity<BatchCreditAdjustResponse> adjustBatch(
            @Valid @RequestBody BatchCreditAdjustRequest request,
            HttpServletRequest httpRequest) {
        BatchCreditAdjustResponse response = aiCreditService.adjustBatchByAdmin(
                request.getUserIds(), request.getAmount(), request.getReason());

        try {
            String actor = getActor();
            String ip = getClientIp(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            String action = String.format("Điều chỉnh Credit hàng loạt cho %d người dùng: %s%d điểm (%d thành công, %d thất bại - Lý do: %s)",
                    response.getTotal(),
                    request.getAmount() >= 0 ? "+" : "",
                    request.getAmount(),
                    response.getSuccessCount(),
                    response.getFailureCount(),
                    (request.getReason() != null && !request.getReason().isBlank()) ? request.getReason() : "Không có lý do");
            systemLogService.log(actor, action, SystemLogLevel.WARNING, "CREDIT", "BATCH", ip, userAgent, response.getFailureCount() == 0 ? "SUCCESS" : "WARNING");
        } catch (Exception e) {
            log.warn("Lỗi khi ghi nhật ký điều chỉnh credit hàng loạt: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    private String getActor() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
                return authentication.getName();
            }
        } catch (Exception ignored) {}
        return "Hệ thống";
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "Sổ cái giao dịch credit")
    @GetMapping("/transactions")
    public ResponseEntity<Page<CreditTransactionResponse>> transactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) CreditTransactionType type,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(aiCreditService.getTransactions(userId, type, pageable));
    }
}
