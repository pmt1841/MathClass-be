package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
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

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudentRemarkJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "STUDENT_REMARK";

    private final StudentRemarkAiService studentRemarkAiService;
    private final AiCreditService aiCreditService;
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

        int actual = calculateActualCredits(response.getCompletionTokens(), message.getReservedCredits());

        return AiJobExecutionResult.builder()
                .resultData(response)
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
