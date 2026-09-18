package com.codegym.mathclass.assignment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AssignmentDrawingRequest {
    private String shapeCode;

    @NotNull(message = "Dữ liệu hình vẽ jsxGraphData không được để trống")
    private Map<String, Object> jsxGraphData;
}
