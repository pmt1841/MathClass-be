package com.codegym.mathclass.aiqueue.service;

import com.codegym.mathclass.aiqueue.dto.AiJobMessage;

public interface AiJobQueueProducer {

    void enqueue(AiJobMessage message);
}
