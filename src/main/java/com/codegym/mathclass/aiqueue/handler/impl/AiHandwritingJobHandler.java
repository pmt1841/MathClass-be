package com.codegym.mathclass.aiqueue.handler.impl;

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

@Slf4j
@Component
@RequiredArgsConstructor
public class AiHandwritingJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "CANVAS_LATEX";

    private final AiSubmissionHandwritingService handwritingService;
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
        if ("SKETCH_GEOMETRY".equalsIgnoreCase(payload.getSubTask())) {
            SketchGeometryResponse response = handwritingService.normalizeSketchToGeometry(
                    payload.getSketchRequest(),
                    payload.getUserId(),
                    false
            );
            resultData = response;
        } else {
            HandwritingLatexResponse response = handwritingService.convertHandwritingToLatex(
                    payload.getLatexRequest(),
                    payload.getUserId(),
                    false
            );
            resultData = response;
        }

        int cost = message.getReservedCredits() > 0 ? message.getReservedCredits() : 2;
        return AiJobExecutionResult.builder()
                .resultData(resultData)
                .actualCredits(cost)
                .build();
    }
}
