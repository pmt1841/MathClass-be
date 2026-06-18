package com.codegym.mathclass.submission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SubmissionDrawingRequest {

    @NotBlank(message = "Shape code must not be empty")
    private String shapeCode;

    @NotNull(message = "jsxGraphData must not be null")
    private Map<String, Object> jsxGraphData;

    private Map<String, Object> metadata;
}
