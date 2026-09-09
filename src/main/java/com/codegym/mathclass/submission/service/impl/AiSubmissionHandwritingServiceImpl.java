package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.ai.strategy.parser.AiResponseParser;
import com.codegym.mathclass.ai.strategy.parser.AiResponseParserFactory;
import com.codegym.mathclass.ai.strategy.parser.AiResponseType;
import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.aiconfig.service.PromptRenderService;
import com.codegym.mathclass.aiconfig.strategy.AiExecutionResult;
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
    private final AiResponseParserFactory aiResponseParserFactory;
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

        AiExecutionResult execResult = aiPromptExecutionService.executePromptWithImageWithResult(
                TASK_CODE,
                prompt,
                request.getImageData(),
                request.getMimeType(),
                userId,
                chargeCredits);

        String rawContent = execResult != null && execResult.content() != null ? execResult.content()
                : aiPromptExecutionService.executePromptWithImage(TASK_CODE, prompt, request.getImageData(), request.getMimeType(), userId);
        Integer completionTokens = execResult != null ? execResult.completionTokens() : null;

        try {
            AiResponseParser<HandwritingLatexResponse> parser = aiResponseParserFactory.getParser(AiResponseType.HANDWRITING);
            HandwritingLatexResponse response = parser.parse(rawContent);
            if (response != null) {
                response.setCompletionTokens(completionTokens);
                response.setRawAiOutput(rawContent);
                if (response.getLatex() != null) {
                    response.setLatex(LaTeXSanitizer.extractCleanLatex(response.getLatex()));
                }
                return response;
            }
        } catch (Exception e) {
            log.warn("Không parse được response handwriting qua Strategy Engine, dùng raw text fallback: {}", e.getMessage());
        }

        String cleanLatex = LaTeXSanitizer.extractCleanLatex(rawContent);

        return HandwritingLatexResponse.builder()
                .latex(cleanLatex)
                .rawAiOutput(rawContent)
                .completionTokens(completionTokens)
                .build();
    }

    @Override
    public SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId) {
        return normalizeSketchToGeometry(request, userId, true);
    }

    @Override
    public SketchGeometryResponse normalizeSketchToGeometry(SketchGeometryRequest request, Long userId, boolean chargeCredits) {
        String prompt = resolvePrompt(PROMPT_SKETCH_GEOMETRY_CODE);

        AiExecutionResult execResult = aiPromptExecutionService.executePromptWithImageWithResult(
                TASK_CODE,
                prompt,
                request.getCanvasImageData(),
                request.getMimeType(),
                userId,
                chargeCredits);

        String rawContent = execResult != null && execResult.content() != null ? execResult.content()
                : aiPromptExecutionService.executePromptWithImage(TASK_CODE, prompt, request.getCanvasImageData(), request.getMimeType(), userId);
        Integer completionTokens = execResult != null ? execResult.completionTokens() : null;

        String cleanJson = AiResponseUtils.extractCleanJson(rawContent);
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
                .completionTokens(completionTokens)
                .build();
    }

    private String resolvePrompt(String promptCode) {
        RenderPromptRequest renderRequest = RenderPromptRequest.builder()
                .promptCode(promptCode)
                .variables(Collections.emptyMap())
                .build();
        RenderPromptResponse res = promptRenderService.renderPrompt(renderRequest);
        if (res == null || res.getRenderedPrompt() == null || res.getRenderedPrompt().isBlank()) {
            throw new ResourceNotFoundException("Tính năng AI hiện đang được bảo trì, vui lòng quay lại sau.");
        }
        return res.getRenderedPrompt();
    }
}
