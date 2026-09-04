package com.codegym.mathclass.aiqueue.service.impl;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.service.AiJobQueueProducer;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

            if ("BATCH_QUESTION_GEN".equalsIgnoreCase(taskCode)) {
                // Tác vụ AI tách đề có maxToken của config là trần input tài liệu (100k tokens),
                // chi phí chuẩn của tác vụ này là costPerCall (mặc định 2 credits).
                reservedCredits = costPerCall > 0 ? costPerCall : 2;
            } else {
                int maxToken = taskConfigRepository.findByTask(taskCode)
                        .map(TaskConfig::getMaxToken)
                        .filter(Objects::nonNull)
                        .orElse(2048);
                reservedCredits = Math.min(10, AiCreditService.estimateCredits(maxToken, costPerCall, tokensPerCredit));
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

        if (!isAdmin && job.getUserId() != null && !Objects.equals(job.getUserId(), requestingUserId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập thông tin tác vụ AI này");
        }

        return job;
    }

    @Override
    public AiJobResultResponse getJobInternal(String jobId) {
        RBucket<String> bucket = redissonClient.getBucket(AI_JOB_PREFIX + jobId);
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
        try {
            AiJobResultResponse job = getJobInternal(jobId);
            job.setStatus(status);
            if (result != null) {
                job.setResult(result);
            }
            if (errorMessage != null) {
                job.setErrorMessage(errorMessage);
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
            RBucket<String> bucket = redissonClient.getBucket(AI_JOB_PREFIX + jobState.getJobId());
            String json = objectMapper.writeValueAsString(jobState);
            bucket.set(json, Duration.ofSeconds(jobTtlSeconds));
        } catch (JsonProcessingException e) {
            log.error("Lỗi tuần tự hóa trạng thái job {}: {}", jobState.getJobId(), e.getMessage());
        }
    }

    private boolean isAdmin(Long userId) {
        return userRepository.findById(userId)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
    }
}
