package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.payload.AiGradingJobPayload;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.service.AiGradingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiGradingJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "SUBMISSION_GRADING";

    private final AiGradingService aiGradingService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String taskCode) {
        return TASK_CODE.equalsIgnoreCase(taskCode);
    }

    @Override
    public AiJobExecutionResult execute(AiJobMessage message) throws Exception {
        log.info("Xử lý tác vụ AI chấm bài (jobId: {})", message.getJobId());
        AiGradingJobPayload payload = objectMapper.readValue(message.getPayloadJson(), AiGradingJobPayload.class);

        AiGradingResponse response = aiGradingService.requestAiGrading(
                payload.getSubmissionId(),
                payload.getRequest(),
                payload.getTeacherId(),
                false
        );

        int cost = message.getReservedCredits() > 0 ? message.getReservedCredits() : 5;
        return AiJobExecutionResult.builder()
                .resultData(response)
                .actualCredits(cost)
                .build();
    }
}
