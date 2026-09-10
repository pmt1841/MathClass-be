package com.codegym.mathclass.assignment.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class AssignmentDrawingRequest {
    private String shapeCode;

    @NotNull(message = "jsxGraphData cannot be null")
    private Map<String, Object> jsxGraphData;
}
