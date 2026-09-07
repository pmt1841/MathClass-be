package com.codegym.mathclass.aiqueue.dto.payload;

import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiHandwritingJobPayload {

    private String subTask;
    private HandwritingLatexRequest latexRequest;
    private SketchGeometryRequest sketchRequest;
    private Long userId;
}
