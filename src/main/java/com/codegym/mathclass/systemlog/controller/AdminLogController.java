package com.codegym.mathclass.systemlog.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.systemlog.dto.response.SystemLogResponse;
import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import com.codegym.mathclass.systemlog.service.SystemLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin - System Logs", description = "APIs quản trị viên: Tra cứu và truy vấn nhật ký hoạt động hệ thống (System Logs)")
@RestController
@ApiVersion(1)
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final SystemLogService systemLogService;

    @Operation(summary = "Truy vấn nhật ký hệ thống (Admin)", description = "Lọc log hệ thống theo mức độ (INFO, WARN, ERROR), loại tài nguyên, người thực hiện và khoảng thời gian")
    @GetMapping
    public ResponseEntity<Page<SystemLogResponse>> getLogs(
            @RequestParam(required = false) SystemLogLevel level,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<SystemLogResponse> logs = systemLogService.getLogs(level, resourceType, actor, startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
    }
}
