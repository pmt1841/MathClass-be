package com.codegym.mathclass.aiqueue.handler.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
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

import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiQuestionJobHandler implements AiJobHandler {

    public static final String TASK_CODE = "QUESTION_GEN";

    private final AiQuestionService aiQuestionService;
    private final AiCreditService aiCreditService;
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
