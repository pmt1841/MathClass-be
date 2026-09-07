package com.codegym.mathclass.aiqueue;

import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiqueue.dto.AiJobExecutionResult;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.handler.AiJobHandler;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.aiqueue.service.impl.AiJobQueueConsumerImpl;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobQueueConsumerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private AiJobService aiJobService;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AiJobHandler jobHandler;

    @Mock
    private RBlockingQueue<AiJobMessage> blockingQueue;

    @Mock
    private RDelayedQueue<AiJobMessage> delayedQueue;

    private AiJobQueueConsumerImpl consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiJobQueueConsumerImpl(
                redissonClient,
                aiJobService,
                aiCreditService,
                notificationService,
                List.of(jobHandler)
        );
    }

    @Test
    @DisplayName("processMessage - Thành công: cập nhật COMPLETED, quyết toán credit, bắn SSE")
    void processMessage_Success() throws Exception {
        String taskCode = "QUESTION_GEN";
        String jobId = "job-001";
        Long userId = 10L;

        AiJobMessage message = AiJobMessage.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .payloadJson("{}")
                .reservedCredits(3)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        AiJobExecutionResult execResult = AiJobExecutionResult.builder()
                .resultData("Đáp án đề thi")
                .actualCredits(3)
                .build();

        when(jobHandler.canHandle(taskCode)).thenReturn(true);
        when(jobHandler.execute(message)).thenReturn(execResult);

        consumer.processMessage(message);

        verify(aiJobService).updateJobStatus(eq(jobId), eq(AiJobStatus.PROCESSING), any(), any(), eq(0));
        verify(aiCreditService).settle(userId, taskCode, 3, 3);
        verify(aiJobService).updateJobStatus(eq(jobId), eq(AiJobStatus.COMPLETED), eq("Đáp án đề thi"), any(), eq(0));
        verify(notificationService).sendAiJobEvent(eq(userId), eq("AI_JOB_COMPLETED"), any());
    }

    @Test
    @DisplayName("processMessage - Lỗi Rate Limit (429): đưa vào Delayed Queue với backoff 5s")
    void processMessage_RateLimit_RetriesWithDelayedQueue() throws Exception {
        String taskCode = "BATCH_QUESTION_GEN";
        String jobId = "job-002";
        Long userId = 10L;

        AiJobMessage message = AiJobMessage.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .payloadJson("{}")
                .reservedCredits(2)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(jobHandler.canHandle(taskCode)).thenReturn(true);
        when(jobHandler.execute(message)).thenThrow(new RuntimeException("429 Too Many Requests: Quota exceeded"));
        doReturn(blockingQueue).when(redissonClient).getBlockingQueue(anyString());
        doReturn(delayedQueue).when(redissonClient).getDelayedQueue(blockingQueue);

        consumer.processMessage(message);

        verify(aiJobService).updateJobStatus(eq(jobId), eq(AiJobStatus.RETRYING), any(), anyString(), eq(1));
        verify(delayedQueue).offer(eq(message), eq(5L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("processMessage - Lỗi không thể retry hoặc vượt quá 3 lần: hoàn trả 100% credit, cập nhật FAILED")
    void processMessage_FatalError_RefundsCreditsAndMarksFailed() throws Exception {
        String taskCode = "SUBMISSION_GRADING";
        String jobId = "job-003";
        Long userId = 10L;

        AiJobMessage message = AiJobMessage.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .payloadJson("{}")
                .reservedCredits(5)
                .retryCount(3)
                .createdAt(Instant.now())
                .build();

        when(jobHandler.canHandle(taskCode)).thenReturn(true);
        when(jobHandler.execute(message)).thenThrow(new BadRequestException("Dữ liệu bài tập không hợp lệ"));

        consumer.processMessage(message);

        verify(aiCreditService).refund(userId, taskCode, 5);
        verify(aiJobService).updateJobStatus(eq(jobId), eq(AiJobStatus.FAILED), any(), anyString(), eq(3));
        verify(notificationService).sendAiJobEvent(eq(userId), eq("AI_JOB_FAILED"), any());
    }

    @Test
    @DisplayName("processMessage - Lỗi BadRequestException mang thông điệp tạm thời (AI tạm thời không khả dụng): vẫn được retry")
    void processMessage_TransientBadRequestException_RetriesWithDelayedQueue() throws Exception {
        String taskCode = "SUBMISSION_GRADING";
        String jobId = "job-004";
        Long userId = 10L;

        AiJobMessage message = AiJobMessage.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .payloadJson("{}")
                .reservedCredits(5)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(jobHandler.canHandle(taskCode)).thenReturn(true);
        when(jobHandler.execute(message)).thenThrow(new BadRequestException("AI chấm bài tạm thời không khả dụng: 429 Too Many Requests"));
        doReturn(blockingQueue).when(redissonClient).getBlockingQueue(anyString());
        doReturn(delayedQueue).when(redissonClient).getDelayedQueue(blockingQueue);

        consumer.processMessage(message);

        verify(aiJobService).updateJobStatus(eq(jobId), eq(AiJobStatus.RETRYING), any(), anyString(), eq(1));
        verify(delayedQueue).offer(eq(message), eq(5L), eq(TimeUnit.SECONDS));
    }
}
