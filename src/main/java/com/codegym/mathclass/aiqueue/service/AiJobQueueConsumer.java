package com.codegym.mathclass.aiqueue.service;

import com.codegym.mathclass.aiqueue.model.AiJobMessage;

public interface AiJobQueueConsumer {

    void start();

    void stop();

    void processMessage(AiJobMessage message);
}
