package com.codegym.mathclass.assignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublishAssignmentRequest {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetClass {
        @NotBlank(message = "Mã lớp không được để trống")
        private String classCode;

        @NotNull(message = "Hạn nộp không được để trống")
        @Future(message = "Hạn nộp phải là thời điểm trong tương lai")
        private LocalDateTime deadline;
    }

    @NotEmpty(message = "Phải chọn ít nhất một lớp để giao bài tập")
    @Valid
    private List<TargetClass> targets;
}
