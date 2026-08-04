package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.dto.request.TaskConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.TaskConfigResponse;
import com.codegym.mathclass.aiconfig.service.TaskConfigService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin - AI Task Configurations", description = "APIs quản trị viên: Cấu hình Model và Tham số cho từng Tác vụ AI (Task Routing)")
@RestController
@ApiVersion(1)
@RequestMapping("/tasks")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TaskConfigController {

    private final TaskConfigService taskConfigService;

    @Operation(summary = "Lấy cấu hình của một Task", description = "Truy vấn thông tin Provider, Model, Temperature, Max Tokens của một tác vụ")
    @GetMapping("/{task}")
    public ResponseEntity<TaskConfigResponse> getTaskConfig(@PathVariable String task) {
        return ResponseEntity.ok(taskConfigService.getTaskConfig(task));
    }

    @Operation(summary = "Cập nhật cấu hình Task", description = "Gán Provider, Model và thiết lập tham số hoạt động cho tác vụ")
    @PutMapping("/{task}")
    public ResponseEntity<TaskConfigResponse> updateTaskConfig(@PathVariable String task, @Valid @RequestBody TaskConfigUpdateRequest request) {
        return ResponseEntity.ok(taskConfigService.updateTaskConfig(task, request));
    }
}
