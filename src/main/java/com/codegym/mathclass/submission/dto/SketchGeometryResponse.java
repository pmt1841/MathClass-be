package com.codegym.mathclass.submission.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SketchGeometryResponse {

    private String shapeType;
    private String geometryJson;
    private Integer completionTokens;
}
