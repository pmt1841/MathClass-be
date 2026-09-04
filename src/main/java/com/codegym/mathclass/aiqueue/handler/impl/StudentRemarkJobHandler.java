package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.payload.StudentRemarkJobPayload;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluationResponse;
import com.codegym.mathclass.classroom.service.StudentRemarkAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRemarkJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "STUDENT_REMARK";

    private final StudentRemarkAiService studentRemarkAiService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean canHandle(String taskCode) {
        return TASK_CODE.equalsIgnoreCase(taskCode);
    }

    @Override
    public AiJobExecutionResult execute(AiJobMessage message) throws Exception {
        log.info("Xử lý tác vụ AI đánh giá tiến độ học sinh (jobId: {})", message.getJobId());
        StudentRemarkJobPayload payload = objectMapper.readValue(message.getPayloadJson(), StudentRemarkJobPayload.class);

        AiStudentRemarkEvaluationResponse response = studentRemarkAiService.evaluateStudentProgress(
                payload.getClassCode(),
                payload.getStudentId(),
                payload.getCurrentUserId(),
                payload.getRequest(),
                false
        );

        int cost = message.getReservedCredits() > 0 ? message.getReservedCredits() : 5;
        return AiJobExecutionResult.builder()
                .resultData(response)
                .actualCredits(cost)
                .build();
    }
}
