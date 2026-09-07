package com.codegym.mathclass.aiqueue.service.impl;

import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.service.AiJobQueueProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobQueueProducerImpl implements AiJobQueueProducer {

    public static final String AI_JOB_QUEUE_NAME = "ai:job:queue";

    private final RedissonClient redissonClient;

    @Override
    public void enqueue(AiJobMessage message) {
        log.info("Đang đẩy tác vụ AI '{}' (jobId: {}) vào hàng đợi '{}'",
                message.getTaskCode(), message.getJobId(), AI_JOB_QUEUE_NAME);
        RBlockingQueue<AiJobMessage> queue = redissonClient.getBlockingQueue(AI_JOB_QUEUE_NAME);
        queue.offer(message);
        log.info("Đã đẩy thành công jobId: {} vào Redis Queue", message.getJobId());
    }
}
