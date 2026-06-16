package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AssignmentDrawingRequest {
    private String shapeCode;

    @NotNull(message = "jsxGraphData cannot be null")
    private Map<String, Object> jsxGraphData;
}
