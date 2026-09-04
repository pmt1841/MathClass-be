package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;

public interface AiSubmissionHandwritingService {

    HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId);

    HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId, boolean chargeCredits);

    SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId);

    SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId, boolean chargeCredits);
}
