package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.payload.AiQuestionJobPayload;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.assignment.dto.AiGeneratedQuestionResponse;
import com.codegym.mathclass.assignment.service.AiQuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuestionJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "QUESTION_GEN";

    private final AiQuestionService aiQuestionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String taskCode) {
        return TASK_CODE.equalsIgnoreCase(taskCode);
    }

    @Override
    public AiJobExecutionResult execute(AiJobMessage message) throws Exception {
        log.info("Xử lý tác vụ AI sinh câu hỏi (jobId: {})", message.getJobId());
        AiQuestionJobPayload payload = objectMapper.readValue(message.getPayloadJson(), AiQuestionJobPayload.class);

        AiGeneratedQuestionResponse response = aiQuestionService.generateQuestion(
                payload.getRequest(),
                payload.getUserId(),
                false
        );

        int cost = message.getReservedCredits() > 0 ? message.getReservedCredits() : 3;
        return AiJobExecutionResult.builder()
                .resultData(response)
                .actualCredits(cost)
                .build();
    }
}
