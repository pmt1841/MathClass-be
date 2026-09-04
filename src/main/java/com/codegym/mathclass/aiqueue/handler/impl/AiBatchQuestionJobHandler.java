package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.payload.AiBatchQuestionJobPayload;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsRequest;
import com.codegym.mathclass.assignment.dto.BatchGenerateQuestionsResponse;
import com.codegym.mathclass.assignment.service.AiBatchQuestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiBatchQuestionJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "BATCH_QUESTION_GEN";

    private final AiBatchQuestionService aiBatchQuestionService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String taskCode) {
        return TASK_CODE.equalsIgnoreCase(taskCode);
    }

    @Override
    public AiJobExecutionResult execute(AiJobMessage message) throws Exception {
        log.info("Xử lý tác vụ AI bóc tách bài tập hàng loạt (jobId: {})", message.getJobId());
        AiBatchQuestionJobPayload payload = objectMapper.readValue(message.getPayloadJson(), AiBatchQuestionJobPayload.class);

        BatchGenerateQuestionsRequest request = new BatchGenerateQuestionsRequest();
        request.setTextContent(payload.getTextContent());
        request.setGrade(payload.getGrade());
        request.setTopic(payload.getTopic());
        request.setQuestionType(payload.getQuestionType());
        request.setIncludeExplanation(payload.getIncludeExplanation());
        request.setIncludeCanvasDiagram(payload.getIncludeCanvasDiagram());

        BatchGenerateQuestionsResponse response = aiBatchQuestionService.batchGenerateQuestions(
                request,
                payload.getUserId(),
                false
        );

        if (payload.getExtractedImages() != null && !payload.getExtractedImages().isEmpty()) {
            response.setExtractedImages(payload.getExtractedImages());
        }

        int cost = message.getReservedCredits() > 0 ? message.getReservedCredits() : 2;
        return AiJobExecutionResult.builder()
                .resultData(response)
                .actualCredits(cost)
                .build();
    }
}
