package com.codegym.mathclass.aiconfig.controller;

import com.codegym.mathclass.aiconfig.entity.Provider;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.common.annotation.ApiVersion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Trạng thái bật/tắt các tính năng AI cho giao diện người dùng (giáo viên/học sinh).
 *
 * Giúp FE ẩn các nút tính năng AI khi admin CHƯA cấu hình hoặc đã TẮT task tương ứng
 * (ví dụ: nút "AI chấm sơ bộ" chỉ hiển thị khi task SUBMISSION_GRADING được bật).
 *
 * Khác với TaskConfigController (admin-only), endpoint này cho phép mọi user đã đăng nhập.
 */
@Tag(name = "AI Features", description = "APIs trạng thái bật/tắt tính năng AI cho frontend")
@RestController
@ApiVersion(1)
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiFeatureController {

    /** Các tính năng AI cần expose cho giao diện người dùng (đồng bộ với SYSTEM_TASKS phía FE). */
    public static final List<String> FEATURE_TASKS = List.of(
            "SUBMISSION_GRADING",
            "STUDENT_HINT",
            "QUESTION_GEN",
            "CANVAS_LATEX",
            "ERROR_ANALYSIS"
    );

    private final TaskConfigRepository taskConfigRepository;

    @Operation(summary = "Trạng thái tính năng AI",
            description = "Trả về map taskCode -> enabled. enabled=true khi task đã được cấu hình, "
                    + "cờ enabled=true và provider liên kết đang ACTIVE.")
    @GetMapping("/features")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Boolean>> getFeatures() {
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (String task : FEATURE_TASKS) {
            features.put(task, isFeatureEnabled(task));
        }
        return ResponseEntity.ok(features);
    }

    private boolean isFeatureEnabled(String task) {
        Optional<TaskConfig> configOpt = taskConfigRepository.findByTask(task);
        if (configOpt.isEmpty()) {
            return false;
        }
        TaskConfig config = configOpt.get();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return false;
        }
        Provider provider = config.getProvider();
        return provider != null && provider.getStatus() == ProviderStatus.ACTIVE;
    }
}
