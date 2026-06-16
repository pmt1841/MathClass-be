package com.codegym.mathclass.assignment.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AssignmentDrawingResponse {
    private Long id;
    private String shapeCode;
    private Map<String, Object> jsxGraphData;
}
