package com.codegym.mathclass.aiqueue.service;

import com.codegym.mathclass.aiqueue.dto.AiJobMessage;

public interface AiJobQueueConsumer {

    void start();

    void stop();

    void processMessage(AiJobMessage message);
}
