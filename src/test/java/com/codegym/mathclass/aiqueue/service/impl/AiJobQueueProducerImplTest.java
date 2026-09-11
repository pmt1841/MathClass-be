package com.codegym.mathclass.aiqueue.service.impl;

import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiJobQueueProducerImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBlockingQueue<AiJobMessage> blockingQueue;

    private AiJobQueueProducerImpl aiJobQueueProducer;

    @BeforeEach
    void setUp() {
        aiJobQueueProducer = new AiJobQueueProducerImpl(redissonClient);
    }

    @Test
    @DisplayName("enqueue đẩy thành công AiJobMessage vào Redis blocking queue")
    void testEnqueueSuccess() {
        AiJobMessage message = AiJobMessage.builder()
                .jobId("job-12345")
                .userId(1L)
                .taskCode("QUESTION_GEN")
                .payloadJson("{\"topic\":\"Algebra\"}")
                .reservedCredits(10)
                .build();

        when(redissonClient.<AiJobMessage>getBlockingQueue(eq(AiJobQueueProducerImpl.AI_JOB_QUEUE_NAME)))
                .thenReturn(blockingQueue);

        aiJobQueueProducer.enqueue(message);

        verify(redissonClient).getBlockingQueue(AiJobQueueProducerImpl.AI_JOB_QUEUE_NAME);
        verify(blockingQueue).offer(message);
    }
}
