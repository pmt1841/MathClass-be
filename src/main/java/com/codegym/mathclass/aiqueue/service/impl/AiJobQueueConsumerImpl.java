package com.codegym.mathclass.aiqueue.service.impl;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.aiqueue.service.AiJobQueueConsumer;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.assignment.exception.AiGenerationException;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobQueueConsumerImpl implements AiJobQueueConsumer, SmartLifecycle {

    public static final String AI_JOB_QUEUE_NAME = "ai:job:queue";
    public static final int MAX_RETRIES = 3;

    @Value("${mathclass.ai.queue.concurrency:4}")
    private int concurrency;

    private final RedissonClient redissonClient;
    private final AiJobService aiJobService;
    private final AiCreditService aiCreditService;
    private final NotificationService notificationService;
    private final List<AiJobHandler> handlers;

    private ExecutorService executorService;
    private RBlockingQueue<AiJobMessage> blockingQueue;
    private RDelayedQueue<AiJobMessage> delayedQueue;
    private volatile boolean isRunning = false;

    @Override
    public synchronized void start() {
        if (isRunning) {
            return;
        }
        isRunning = true;
        blockingQueue = redissonClient.getBlockingQueue(AI_JOB_QUEUE_NAME);
        delayedQueue = redissonClient.getDelayedQueue(blockingQueue);

        executorService = Executors.newFixedThreadPool(concurrency);
        log.info("Khởi động AI Job Queue Consumer với {} workers song song", concurrency);

        for (int i = 0; i < concurrency; i++) {
            executorService.submit(this::runWorkerLoop);
        }
    }

    @Override
    public synchronized void stop() {
        if (!isRunning) {
            return;
        }
        isRunning = false;
        log.info("Đang dừng AI Job Queue Consumer...");
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(20, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("Đã dừng AI Job Queue Consumer an toàn");
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public int getPhase() {
        // Phase cao để khi shutdown, consumer dừng nhận message mới trước khi RedissonClient/DataSource bị đóng
        return Integer.MAX_VALUE - 100;
    }

    private void runWorkerLoop() {
        RBlockingQueue<AiJobMessage> queue = (blockingQueue != null)
                ? blockingQueue
                : redissonClient.getBlockingQueue(AI_JOB_QUEUE_NAME);
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                AiJobMessage message = queue.poll(2, TimeUnit.SECONDS);
                if (message != null) {
                    processMessage(message);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Lỗi trong vòng lặp AI Worker: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void processMessage(AiJobMessage message) {
        String jobId = message.getJobId();
        log.info("AI Worker bắt đầu xử lý jobId: {} (task: {})", jobId, message.getTaskCode());

        aiJobService.updateJobStatus(jobId, AiJobStatus.PROCESSING, null, null, message.getRetryCount());

        AiJobHandler handler = handlers.stream()
                .filter(h -> h.canHandle(message.getTaskCode()))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            log.error("Không tìm thấy handler cho task: {}", message.getTaskCode());
            handleFinalFailure(message, "Hệ thống chưa hỗ trợ xử lý tác vụ: " + message.getTaskCode());
            return;
        }

        try {
            AiJobExecutionResult result = handler.execute(message);

            if (message.getReservedCredits() > 0 && message.getUserId() != null) {
                int actual = result.getActualCredits() != null
                        ? result.getActualCredits()
                        : message.getReservedCredits();
                log.info("Quyết toán credit cho user {} (jobId: {}): reserved={}, actual={}",
                        message.getUserId(), jobId, message.getReservedCredits(), actual);
                aiCreditService.settle(message.getUserId(), message.getTaskCode(), message.getReservedCredits(), actual);
            }

            aiJobService.updateJobStatus(jobId, AiJobStatus.COMPLETED, result.getResultData(), null, message.getRetryCount());

            Map<String, Object> eventData = new HashMap<>();
            eventData.put("eventType", "AI_JOB_COMPLETED");
            eventData.put("jobId", jobId);
            eventData.put("taskCode", message.getTaskCode());
            eventData.put("status", AiJobStatus.COMPLETED.name());
            eventData.put("result", result.getResultData());

            notificationService.sendAiJobEvent(message.getUserId(), "AI_JOB_COMPLETED", eventData);
            log.info("Hoàn tất thành công tác vụ AI jobId: {}", jobId);

        } catch (Exception e) {
            log.error("Lỗi khi xử lý tác vụ AI jobId {}: {}", jobId, e.getMessage(), e);
            if (isRetryable(e) && message.getRetryCount() < MAX_RETRIES) {
                handleRetry(message, e);
            } else {
                handleFinalFailure(message, e.getMessage() != null ? e.getMessage() : "Xử lý tác vụ AI thất bại");
            }
        }
    }

    private void handleRetry(AiJobMessage message, Exception e) {
        int nextRetry = message.getRetryCount() + 1;
        message.setRetryCount(nextRetry);

        long delaySeconds = nextRetry == 1 ? 5L : (nextRetry == 2 ? 15L : 45L);
        log.warn("Đưa jobId: {} vào Delayed Queue thử lại lần {} sau {} giây. Lý do: {}",
                message.getJobId(), nextRetry, delaySeconds, e.getMessage());

        aiJobService.updateJobStatus(
                message.getJobId(),
                AiJobStatus.RETRYING,
                null,
                "Đang tự động thử lại (lần " + nextRetry + "/" + MAX_RETRIES + "): " + e.getMessage(),
                nextRetry
        );

        RDelayedQueue<AiJobMessage> delayQ = (delayedQueue != null)
                ? delayedQueue
                : redissonClient.getDelayedQueue(redissonClient.getBlockingQueue(AI_JOB_QUEUE_NAME));
        delayQ.offer(message, delaySeconds, TimeUnit.SECONDS);
    }

    private void handleFinalFailure(AiJobMessage message, String errorMessage) {
        String jobId = message.getJobId();
        log.error("Tác vụ AI thất bại hoàn toàn (jobId: {}): {}", jobId, errorMessage);

        if (message.getReservedCredits() > 0 && message.getUserId() != null) {
            log.info("Hoàn lại {} credits cho user {} sau khi job {} thất bại",
                    message.getReservedCredits(), message.getUserId(), jobId);
            aiCreditService.refund(message.getUserId(), message.getTaskCode(), message.getReservedCredits());
        }

        aiJobService.updateJobStatus(jobId, AiJobStatus.FAILED, null, errorMessage, message.getRetryCount());

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("eventType", "AI_JOB_FAILED");
        eventData.put("jobId", jobId);
        eventData.put("taskCode", message.getTaskCode());
        eventData.put("status", AiJobStatus.FAILED.name());
        eventData.put("errorMessage", errorMessage);

        notificationService.sendAiJobEvent(message.getUserId(), "AI_JOB_FAILED", eventData);
    }

    private boolean isRetryable(Exception e) {
        if (e instanceof AccessDeniedException
                || e instanceof ResourceNotFoundException
                || e instanceof IllegalArgumentException) {
            return false;
        }

        if (e instanceof AiGenerationException aiEx) {
            int code = aiEx.getStatusCode();
            if (code == 429 || code == 502 || code == 503 || code == 504) {
                return true;
            }
            if (code >= 400 && code < 500) {
                return false;
            }
        }

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        boolean hasTransientKeywords = msg.contains("429")
                || msg.contains("too many requests")
                || msg.contains("timeout")
                || msg.contains("timed out")
                || msg.contains("quota")
                || msg.contains("resource_exhausted")
                || msg.contains("503")
                || msg.contains("502")
                || msg.contains("504")
                || msg.contains("tạm thời không khả dụng")
                || msg.contains("phản hồi rỗng");

        if (e instanceof BadRequestException) {
            return hasTransientKeywords;
        }

        return hasTransientKeywords;
    }
}
