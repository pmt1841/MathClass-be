package com.codegym.mathclass.aiqueue.service.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.response.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.model.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.response.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.service.AiJobQueueProducer;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.aiqueue.model.payload.AiBatchQuestionJobPayload;
import com.codegym.mathclass.aiqueue.model.payload.AiQuestionJobPayload;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.codegym.mathclass.aiqueue.dto.response.AiJobCancelResponse;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.StringCodec;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiJobServiceImpl implements AiJobService {

    public static final String AI_JOB_PREFIX = "ai:job:";

    @Value("${mathclass.ai.queue.job-ttl-seconds:86400}")
    private long jobTtlSeconds;

    private final RedissonClient redissonClient;
    private final AiJobQueueProducer aiJobQueueProducer;
    private final AiCreditService aiCreditService;
    private final TaskConfigRepository taskConfigRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public AiJobSubmitResponse submitJob(String taskCode, Long userId, Object payloadDto) {
        String jobId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        int reservedCredits = 0;
        Optional<AiCreditConfig> creditCfg = aiCreditService.getCreditConfig(taskCode);
        boolean charge = creditCfg.isPresent()
                && Boolean.TRUE.equals(creditCfg.get().getEnabled())
                && userId != null
                && !isAdmin(userId);

        if (charge) {
            int costPerCall = creditCfg.get().getCostPerCall() != null ? creditCfg.get().getCostPerCall() : 0;
            Integer tokensPerCredit = creditCfg.get().getTokensPerCredit();

            int userPromptTokens = 0;
            if (payloadDto instanceof AiQuestionJobPayload questionPayload && questionPayload.getRequest() != null) {
                userPromptTokens = AiCreditService.estimatePromptTokens(questionPayload.getRequest().getPrompt());
            } else if (payloadDto instanceof AiBatchQuestionJobPayload batchPayload) {
                userPromptTokens = AiCreditService.estimatePromptTokens(batchPayload.getTextContent());
            }

            if ("BATCH_QUESTION_GEN".equalsIgnoreCase(taskCode)) {
                // Tác vụ AI tách đề: dự trù maxToken = 2000 output khi reserve để tránh lệch pha với settle
                reservedCredits = AiCreditService.estimateCredits(userPromptTokens, 2000, costPerCall, tokensPerCredit);
            } else {
                int maxToken = taskConfigRepository.findByTask(taskCode)
                        .map(TaskConfig::getMaxToken)
                        .filter(Objects::nonNull)
                        .orElse(2048);
                reservedCredits = Math.min(10, AiCreditService.estimateCredits(userPromptTokens, maxToken, costPerCall, tokensPerCredit));
            }

            if (reservedCredits > 0) {
                log.info("Đặt chỗ {} credits cho user {} với tác vụ '{}' (jobId: {})",
                        reservedCredits, userId, taskCode, jobId);
                aiCreditService.reserve(userId, taskCode, reservedCredits);
            }
        }

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(payloadDto);
        } catch (JsonProcessingException e) {
            if (reservedCredits > 0) {
                aiCreditService.refund(userId, taskCode, reservedCredits);
            }
            log.error("Lỗi parse payload tác vụ AI: {}", e.getMessage());
            throw new IllegalArgumentException("Dữ liệu yêu cầu không thể chuyển đổi sang định dạng JSON");
        }

        AiJobResultResponse jobState = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .status(AiJobStatus.QUEUED)
                .reservedCredits(reservedCredits)
                .retryCount(0)
                .createdAt(now)
                .build();

        saveJobState(jobState);

        AiJobMessage message = AiJobMessage.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode(taskCode)
                .payloadJson(payloadJson)
                .retryCount(0)
                .reservedCredits(reservedCredits)
                .createdAt(now)
                .build();

        try {
            aiJobQueueProducer.enqueue(message);
        } catch (Exception e) {
            if (reservedCredits > 0) {
                aiCreditService.refund(userId, taskCode, reservedCredits);
            }
            log.error("Lỗi khi đẩy tác vụ AI vào Redis Queue: {}", e.getMessage());
            throw new IllegalStateException("Không thể đưa tác vụ vào hàng đợi Redis", e);
        }

        return AiJobSubmitResponse.builder()
                .jobId(jobId)
                .taskCode(taskCode)
                .status(AiJobStatus.QUEUED)
                .createdAt(now)
                .message("Yêu cầu đã được tiếp nhận thành công và đưa vào hàng đợi xử lý.")
                .build();
    }

    @Override
    public AiJobResultResponse getJobStatus(String jobId, Long requestingUserId, boolean isAdmin) {
        AiJobResultResponse job = getJobInternal(jobId);

        if (!isAdmin && (job.getUserId() == null || !Objects.equals(job.getUserId(), requestingUserId))) {
            throw new AccessDeniedException("Bạn không có quyền truy cập thông tin tác vụ AI này");
        }

        return job;
    }

    private AiJobResultResponse getJobInternal(String jobId) {
        RBucket<String> bucket = redissonClient.getBucket(AI_JOB_PREFIX + jobId, StringCodec.INSTANCE);
        if (!bucket.isExists()) {
            throw new ResourceNotFoundException("Không tìm thấy tác vụ AI hoặc tác vụ đã hết hạn lưu trữ");
        }

        try {
            return objectMapper.readValue(bucket.get(), AiJobResultResponse.class);
        } catch (JsonProcessingException e) {
            log.error("Lỗi đọc trạng thái job từ Redis cho jobId {}: {}", jobId, e.getMessage());
            throw new IllegalStateException("Không thể đọc dữ liệu trạng thái tác vụ từ Redis", e);
        }
    }

    @Override
    public void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage) {
        updateJobStatus(jobId, status, result, errorMessage, null);
    }

    @Override
    public void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage, Integer retryCount) {
        try {
            AiJobResultResponse job = getJobInternal(jobId);
            job.setStatus(status);
            if (result != null) {
                job.setResult(result);
            }
            if (errorMessage != null) {
                job.setErrorMessage(errorMessage);
            }
            if (retryCount != null) {
                job.setRetryCount(retryCount);
            }
            if (status == AiJobStatus.COMPLETED || status == AiJobStatus.FAILED) {
                job.setCompletedAt(Instant.now());
            }
            saveJobState(job);
        } catch (Exception e) {
            log.error("Lỗi cập nhật trạng thái job {}: {}", jobId, e.getMessage());
        }
    }

    private void saveJobState(AiJobResultResponse jobState) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(AI_JOB_PREFIX + jobState.getJobId(), StringCodec.INSTANCE);
            String json = objectMapper.writeValueAsString(jobState);
            bucket.set(json, Duration.ofSeconds(jobTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Lỗi tuần tự hóa trạng thái job {}: {}", jobState.getJobId(), e.getMessage());
        }
    }

    @Override
    public AiJobCancelResponse cancelJob(String jobId, Long requestingUserId, boolean isAdmin) {
        return cancelJob(jobId, requestingUserId, isAdmin, false);
    }

    @Override
    public AiJobCancelResponse cancelJob(String jobId, Long requestingUserId, boolean isAdmin, boolean force) {
        RLock lock = redissonClient.getLock("ai:job:lock:" + jobId);
        try {
            if (!lock.tryLock(5, 5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Hệ thống đang bận xử lý tác vụ, vui lòng thử lại sau giây lát");
            }
            AiJobResultResponse job = getJobInternal(jobId);
            if (!isAdmin && (job.getUserId() == null || !Objects.equals(job.getUserId(), requestingUserId))) {
                throw new AccessDeniedException("Bạn không có quyền hủy tác vụ AI này");
            }

            if (job.getStatus() == AiJobStatus.COMPLETED) {
                return AiJobCancelResponse.builder()
                        .jobId(jobId)
                        .status(AiJobStatus.COMPLETED)
                        .cancelled(false)
                        .refunded(false)
                        .refundedCredits(0)
                        .code("COMPLETED")
                        .message("Tác vụ đã hoàn thành, không thể hủy.")
                        .build();
            }

            if (job.getStatus() == AiJobStatus.CANCELLED) {
                return AiJobCancelResponse.builder()
                        .jobId(jobId)
                        .status(AiJobStatus.CANCELLED)
                        .cancelled(true)
                        .refunded(false)
                        .refundedCredits(0)
                        .code("ALREADY_CANCELLED")
                        .message("Tác vụ đã được hủy trước đó.")
                        .build();
            }

            int reserved = job.getReservedCredits() != null ? job.getReservedCredits() : 0;

            // Atomic CAS: Chỉ hoàn tiền nếu trạng thái thực sự còn là QUEUED
            if (job.getStatus() == AiJobStatus.QUEUED) {
                job.setStatus(AiJobStatus.CANCELLED);
                job.setErrorMessage("Người dùng đã hủy tác vụ khi đang trong hàng chờ");
                job.setCompletedAt(Instant.now());
                saveJobState(job);

                if (reserved > 0 && job.getUserId() != null) {
                    aiCreditService.refund(job.getUserId(), job.getTaskCode(), reserved);
                }

                log.info("Đã hủy job {} trong hàng đợi và hoàn lại {} credit cho user {}", jobId, reserved, job.getUserId());
                return AiJobCancelResponse.builder()
                        .jobId(jobId)
                        .status(AiJobStatus.CANCELLED)
                        .cancelled(true)
                        .refunded(true)
                        .refundedCredits(reserved)
                        .code("SUCCESS")
                        .message("Đã hủy tác vụ trong hàng chờ và hoàn lại " + reserved + " credit.")
                        .build();
            } else if (job.getStatus() == AiJobStatus.PROCESSING || job.getStatus() == AiJobStatus.RETRYING) {
                // Nếu Worker đã bốc job sang PROCESSING và user không truyền force=true: Từ chối hủy, không hoàn credit
                if (!force) {
                    log.info("Từ chối hủy job {} vì Worker đã bắt đầu PROCESSING (force=false)", jobId);
                    return AiJobCancelResponse.builder()
                            .jobId(jobId)
                            .status(job.getStatus())
                            .cancelled(false)
                            .refunded(false)
                            .refundedCredits(0)
                            .code("ALREADY_PROCESSING")
                            .message("Tác vụ AI đã bắt đầu xử lý. Hệ thống tiếp tục thực hiện để tránh lãng phí credit.")
                            .build();
                }

                // Trường hợp người dùng đã thấy cảnh báo và chủ động chọn force=true (vẫn hủy dù mất credit)
                job.setStatus(AiJobStatus.CANCELLED);
                job.setErrorMessage("Người dùng đã dừng tác vụ khi đang xử lý (không hoàn credit)");
                job.setCompletedAt(Instant.now());
                saveJobState(job);

                log.info("Đã dừng job {} khi đang xử lý cho user {} theo yêu cầu force (không hoàn credit)", jobId, job.getUserId());
                return AiJobCancelResponse.builder()
                        .jobId(jobId)
                        .status(AiJobStatus.CANCELLED)
                        .cancelled(true)
                        .refunded(false)
                        .refundedCredits(0)
                        .code("CANCELLED_WITHOUT_REFUND")
                        .message("Đã hủy tác vụ đang xử lý (không hoàn credit).")
                        .build();
            } else {
                return AiJobCancelResponse.builder()
                        .jobId(jobId)
                        .status(job.getStatus())
                        .cancelled(false)
                        .refunded(false)
                        .refundedCredits(0)
                        .code("FAILED")
                        .message("Tác vụ đã kết thúc với trạng thái " + job.getStatus() + ", không thể hủy.")
                        .build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Yêu cầu hủy bị gián đoạn", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
    }
}
