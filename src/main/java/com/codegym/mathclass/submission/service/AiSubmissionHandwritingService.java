package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.request.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.response.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.request.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.response.SketchGeometryResponse;

public interface AiSubmissionHandwritingService {

    HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId);

    HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId, boolean chargeCredits);

    SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId);

    SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId, boolean chargeCredits);
}
