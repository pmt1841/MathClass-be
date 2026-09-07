package com.codegym.mathclass.aiqueue.handler;

import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;

public interface AiJobHandler {

    boolean canHandle(String taskCode);

    AiJobExecutionResult execute(AiJobMessage message) throws Exception;
}
