package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.payload.AiHandwritingJobPayload;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.submission.dto.HandwritingLatexResponse;
import com.codegym.mathclass.submission.dto.SketchGeometryResponse;
import com.codegym.mathclass.submission.service.AiSubmissionHandwritingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiHandwritingJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "CANVAS_LATEX";

    private final AiSubmissionHandwritingService handwritingService;
    private final AiCreditService aiCreditService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String taskCode) {
        return TASK_CODE.equalsIgnoreCase(taskCode);
    }

    @Override
    public AiJobExecutionResult execute(AiJobMessage message) throws Exception {
        log.info("Xử lý tác vụ AI Canvas / OCR chữ viết tay (jobId: {})", message.getJobId());
        AiHandwritingJobPayload payload = objectMapper.readValue(message.getPayloadJson(), AiHandwritingJobPayload.class);

        Object resultData;
        Integer completionTokens;
        if ("SKETCH_GEOMETRY".equalsIgnoreCase(payload.getSubTask())) {
            SketchGeometryResponse response = handwritingService.normalizeSketchToGeometry(
                    payload.getSketchRequest(),
                    payload.getUserId(),
                    false
            );
            resultData = response;
            completionTokens = response.getCompletionTokens();
        } else {
            HandwritingLatexResponse response = handwritingService.convertHandwritingToLatex(
                    payload.getLatexRequest(),
                    payload.getUserId(),
                    false
            );
            resultData = response;
            completionTokens = response.getCompletionTokens();
        }

        int actual = calculateActualCredits(completionTokens, message.getReservedCredits());

        return AiJobExecutionResult.builder()
                .resultData(resultData)
                .actualCredits(actual)
                .build();
    }

    private int calculateActualCredits(Integer completionTokens, int reservedCredits) {
        Optional<AiCreditConfig> creditCfg = aiCreditService.getCreditConfig(TASK_CODE);
        int costPerCall = creditCfg.map(AiCreditConfig::getCostPerCall).filter(Objects::nonNull).orElse(0);
        Integer tokensPerCredit = creditCfg.map(AiCreditConfig::getTokensPerCredit).orElse(null);
        int computed = AiCreditService.computeCredits(completionTokens, costPerCall, tokensPerCredit);
        return reservedCredits > 0 ? Math.min(computed, reservedCredits) : computed;
    }
}
