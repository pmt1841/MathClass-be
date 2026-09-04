package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.HandwritingLatexRequest;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryRequest;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.codegym.mathclass.utils.LaTeXSanitizer;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSubmissionHandwritingServiceImpl implements AiSubmissionHandwritingService {

    public static final String TASK_CODE = "CANVAS_LATEX";
    public static final String PROMPT_HANDWRITING_LATEX_CODE = "PROMPT_HANDWRITING_LATEX";
    public static final String PROMPT_SKETCH_GEOMETRY_CODE = "PROMPT_SKETCH_GEOMETRY";

    private final AiPromptExecutionService aiPromptExecutionService;
    private final PromptRenderService promptRenderService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
            .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true);

    @Override
    public HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId) {
        return convertHandwritingToLatex(request, userId, true);
    }

    @Override
    public HandwritingLatexResponse convertHandwritingToLatex(HandwritingLatexRequest request, Long userId, boolean chargeCredits) {
        String prompt = resolvePrompt(PROMPT_HANDWRITING_LATEX_CODE);

        String aiOutput = chargeCredits
                ? aiPromptExecutionService.executePromptWithImage(
                        TASK_CODE,
                        prompt,
                        request.getImageData(),
                        request.getMimeType(),
                        userId)
                : aiPromptExecutionService.executePromptWithImage(
                        TASK_CODE,
                        prompt,
                        request.getImageData(),
                        request.getMimeType(),
                        userId,
                        false);

        String cleanLatex = LaTeXSanitizer.extractCleanLatex(aiOutput);

        return HandwritingLatexResponse.builder()
                .latex(cleanLatex)
                .rawAiOutput(aiOutput)
                .build();
    }

    @Override
    public SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId) {
        return normalizeSketchToGeometry(request, userId, true);
    }

    @Override
    public SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId, boolean chargeCredits) {
        String prompt = resolvePrompt(PROMPT_SKETCH_GEOMETRY_CODE);

        String aiOutput = chargeCredits
                ? aiPromptExecutionService.executePromptWithImage(
                        TASK_CODE,
                        prompt,
                        request.getCanvasImageData(),
                        request.getMimeType(),
                        userId)
                : aiPromptExecutionService.executePromptWithImage(
                        TASK_CODE,
                        prompt,
                        request.getCanvasImageData(),
                        request.getMimeType(),
                        userId,
                        false);

        String cleanJson = AiResponseUtils.extractCleanJson(aiOutput);
        String shapeType = "CUSTOM_GEOMETRY";
        try {
            JsonNode node = objectMapper.readTree(cleanJson);
            if (node.has("shapeType")) {
                shapeType = node.path("shapeType").asText("CUSTOM_GEOMETRY");
            }
        } catch (Exception e) {
            log.warn("Không thể parse JSON shapeType từ AI output: {}", e.getMessage());
        }

        return SketchGeometryResponse.builder()
                .shapeType(shapeType)
                .geometryJson(cleanJson)
                .build();
    }

    private String resolvePrompt(String promptCode) {
        RenderPromptRequest renderRequest = RenderPromptRequest.builder()
                .promptCode(promptCode)
                .variables(Collections.emptyMap())
                .build();
        RenderPromptResponse res = promptRenderService.renderPrompt(renderRequest);
        if (res == null || res.getRenderedPrompt() == null || res.getRenderedPrompt().isBlank()) {
            throw new ResourceNotFoundException("Chưa cấu hình System Prompt '" + promptCode + "' trong CSDL.");
        }
        return res.getRenderedPrompt();
    }
}
