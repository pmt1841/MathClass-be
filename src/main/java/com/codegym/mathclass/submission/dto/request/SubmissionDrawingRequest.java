package com.codegym.mathclass.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SubmissionDrawingRequest {

    @NotBlank(message = "Mã hình vẽ không được để trống")
    private String shapeCode;

    @NotNull(message = "Dữ liệu hình vẽ jsxGraphData không được để trống")
    private Map<String, Object> jsxGraphData;

    private Map<String, Object> metadata;
}
