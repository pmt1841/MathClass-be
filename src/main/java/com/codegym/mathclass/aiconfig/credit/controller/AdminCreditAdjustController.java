package com.codegym.mathclass.aiconfig.credit.controller;

import com.codegym.mathclass.aiconfig.credit.dto.request.CreditAdjustRequest;
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

@Tag(name = "Admin - Credit Adjust", description = "APIs quản trị viên: điều chỉnh credit thủ công và xem sổ cái giao dịch")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/credits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCreditAdjustController {

    private final AiCreditService aiCreditService;

    @Operation(summary = "Điều chỉnh credit thủ công (grant / hoàn tiền)")
    @PostMapping("/adjust")
    public ResponseEntity<Map<String, String>> adjust(@Valid @RequestBody CreditAdjustRequest request) {
        aiCreditService.adjustByAdmin(request.getUserId(), request.getAmount(), request.getReason());
        return ResponseEntity.ok(Map.of("message", "Điều chỉnh credit thành công"));
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
