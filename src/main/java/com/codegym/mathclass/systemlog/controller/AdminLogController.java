package com.codegym.mathclass.systemlog.controller;

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
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final SystemLogService systemLogService;

    @GetMapping
    public ResponseEntity<Page<SystemLogResponse>> getLogs(
            @RequestParam(required = false) SystemLogLevel level,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<SystemLogResponse> logs = systemLogService.getLogs(level, actor, startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
    }
}
