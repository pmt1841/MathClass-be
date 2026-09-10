package com.codegym.mathclass.aiqueue.handler;

import com.codegym.mathclass.aiqueue.model.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;

public interface AiJobHandler {

    boolean canHandle(String taskCode);

    AiJobExecutionResult execute(AiJobMessage message) throws Exception;
}
