package com.codegym.mathclass.aiqueue;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiqueue.dto.response.AiJobCancelResponse;
import com.codegym.mathclass.aiqueue.model.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.response.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.model.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.response.AiJobSubmitResponse;
import com.codegym.mathclass.aiqueue.service.AiJobQueueProducer;
import com.codegym.mathclass.aiqueue.service.impl.AiJobServiceImpl;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private AiJobQueueProducer aiJobQueueProducer;

    @Mock
    private AiCreditService aiCreditService;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RBucket<String> bucket;

    @Mock
    private RLock lock;

    private ObjectMapper objectMapper;

    @InjectMocks
    private AiJobServiceImpl aiJobService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        ReflectionTestUtils.setField(aiJobService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(aiJobService, "jobTtlSeconds", 86400L);
    }

    @Test
    @DisplayName("submitJob - Thành công với việc đặt chỗ credit và đẩy vào Redis Queue")
    void submitJob_Success_WithCreditReservation() {
        Long userId = 10L;
        String taskCode = "QUESTION_GEN";
        Map<String, String> payload = Map.of("prompt", "Bài toán đại số");

        AiCreditConfig creditConfig = new AiCreditConfig();
        creditConfig.setEnabled(true);
        creditConfig.setCostPerCall(3);
        creditConfig.setTokensPerCredit(1000);

        TaskConfig taskConfig = new TaskConfig();
        taskConfig.setMaxToken(2048);

        User teacher = new User();
        teacher.setId(userId);
        teacher.setRole(Role.TEACHER);

        when(aiCreditService.getCreditConfig(taskCode)).thenReturn(Optional.of(creditConfig));
        when(userRepository.findById(userId)).thenReturn(Optional.of(teacher));
        when(taskConfigRepository.findByTask(taskCode)).thenReturn(Optional.of(taskConfig));
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));

        AiJobSubmitResponse response = aiJobService.submitJob(taskCode, userId, payload);

        assertNotNull(response);
        assertNotNull(response.getJobId());
        assertEquals(taskCode, response.getTaskCode());
        assertEquals(AiJobStatus.QUEUED, response.getStatus());

        verify(aiCreditService).reserve(eq(userId), eq(taskCode), any(Integer.class));
        verify(bucket).set(anyString(), any(Duration.class));
        verify(aiJobQueueProducer).enqueue(any(AiJobMessage.class));
    }

    @Test
    @DisplayName("submitJob - Thành công cho Admin (không trừ credit)")
    void submitJob_Success_AdminExemptFromCredits() {
        Long userId = 1L;
        String taskCode = "QUESTION_GEN";
        Map<String, String> payload = Map.of("prompt", "Đề thi khảo sát");

        AiCreditConfig creditConfig = new AiCreditConfig();
        creditConfig.setEnabled(true);
        creditConfig.setCostPerCall(3);

        User admin = new User();
        admin.setId(userId);
        admin.setRole(Role.ADMIN);

        when(aiCreditService.getCreditConfig(taskCode)).thenReturn(Optional.of(creditConfig));
        when(userRepository.findById(userId)).thenReturn(Optional.of(admin));
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));

        AiJobSubmitResponse response = aiJobService.submitJob(taskCode, userId, payload);

        assertNotNull(response);
        assertEquals(AiJobStatus.QUEUED, response.getStatus());

        verify(aiCreditService, never()).reserve(any(), any(), any(Integer.class));
        verify(aiJobQueueProducer).enqueue(any(AiJobMessage.class));
    }

    @Test
    @DisplayName("getJobStatus - Trả về chi tiết job khi đúng chủ sở hữu")
    void getJobStatus_Success_Owner() throws Exception {
        String jobId = "job-123";
        Long userId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.COMPLETED)
                .result("Kết quả câu hỏi")
                .createdAt(Instant.now())
                .build();

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        AiJobResultResponse result = aiJobService.getJobStatus(jobId, userId, false);

        assertNotNull(result);
        assertEquals(jobId, result.getJobId());
        assertEquals(AiJobStatus.COMPLETED, result.getStatus());
    }

    @Test
    @DisplayName("getJobStatus - Cho phép Admin xem job của bất kỳ ai")
    void getJobStatus_Success_Admin() throws Exception {
        String jobId = "job-123";
        Long ownerId = 10L;
        Long adminId = 1L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(ownerId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.PROCESSING)
                .createdAt(Instant.now())
                .build();

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        AiJobResultResponse result = aiJobService.getJobStatus(jobId, adminId, true);

        assertNotNull(result);
        assertEquals(jobId, result.getJobId());
    }

    @Test
    @DisplayName("getJobStatus - Ném AccessDeniedException khi người dùng khác xem trộm job")
    void getJobStatus_ThrowsAccessDenied_OtherUser() throws Exception {
        String jobId = "job-123";
        Long ownerId = 10L;
        Long strangerId = 99L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(ownerId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.QUEUED)
                .build();

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        assertThrows(AccessDeniedException.class,
                () -> aiJobService.getJobStatus(jobId, strangerId, false));
    }

    @Test
    @DisplayName("getJobStatus - Ném AccessDeniedException khi job không có userId (system job) và người gọi không phải Admin")
    void getJobStatus_ThrowsAccessDenied_NullOwnerNotAdmin() throws Exception {
        String jobId = "job-system-123";
        Long strangerId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(null)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.QUEUED)
                .build();

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        assertThrows(AccessDeniedException.class,
                () -> aiJobService.getJobStatus(jobId, strangerId, false));
    }

    @Test
    @DisplayName("updateJobStatus - Cập nhật trạng thái và retryCount vào Redis")
    void updateJobStatus_UpdatesRetryCount() throws Exception {
        String jobId = "job-retry-123";

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(10L)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.PROCESSING)
                .retryCount(0)
                .build();

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        aiJobService.updateJobStatus(jobId, AiJobStatus.RETRYING, null, "Lỗi quota", 2);

        verify(bucket).set(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("cancelJob - Thành công khi tác vụ ở QUEUED: Hoàn lại 100% credit đã cọc")
    void cancelJob_Queued_CancelsAndRefundsCredits() throws Exception {
        String jobId = "job-queued-1";
        Long userId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode("BATCH_QUESTION_GEN")
                .status(AiJobStatus.QUEUED)
                .reservedCredits(5)
                .build();

        doReturn(lock).when(redissonClient).getLock("ai:job:lock:" + jobId);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        AiJobCancelResponse response =
                aiJobService.cancelJob(jobId, userId, false);

        assertNotNull(response);
        assertEquals(true, response.isCancelled());
        assertEquals(true, response.isRefunded());
        assertEquals(5, response.getRefundedCredits());
        assertEquals(AiJobStatus.CANCELLED, response.getStatus());
        assertEquals("SUCCESS", response.getCode());

        verify(aiCreditService).refund(userId, "BATCH_QUESTION_GEN", 5);
        verify(bucket).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("cancelJob - Atomic CAS: Khi tác vụ ở PROCESSING và không force -> Trả về ALREADY_PROCESSING, không hủy và không mất credit")
    void cancelJob_Processing_WithoutForce_ReturnsAlreadyProcessing() throws Exception {
        String jobId = "job-proc-1";
        Long userId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.PROCESSING)
                .reservedCredits(3)
                .build();

        doReturn(lock).when(redissonClient).getLock("ai:job:lock:" + jobId);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        // Gọi cancel mặc định (force = false)
        AiJobCancelResponse response =
                aiJobService.cancelJob(jobId, userId, false, false);

        assertNotNull(response);
        assertEquals(false, response.isCancelled());
        assertEquals(false, response.isRefunded());
        assertEquals(0, response.getRefundedCredits());
        assertEquals(AiJobStatus.PROCESSING, response.getStatus());
        assertEquals("ALREADY_PROCESSING", response.getCode());

        verify(aiCreditService, never()).refund(any(), any(), anyInt());
        verify(bucket, never()).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("cancelJob - Khi tác vụ ở PROCESSING và force = true -> Hủy không hoàn credit")
    void cancelJob_Processing_WithForce_CancelsWithoutRefund() throws Exception {
        String jobId = "job-proc-2";
        Long userId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.PROCESSING)
                .reservedCredits(3)
                .build();

        doReturn(lock).when(redissonClient).getLock("ai:job:lock:" + jobId);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        // Người dùng đã xác nhận chấp nhận hủy dù mất credit (force = true)
        AiJobCancelResponse response =
                aiJobService.cancelJob(jobId, userId, false, true);

        assertNotNull(response);
        assertEquals(true, response.isCancelled());
        assertEquals(false, response.isRefunded());
        assertEquals(0, response.getRefundedCredits());
        assertEquals(AiJobStatus.CANCELLED, response.getStatus());
        assertEquals("CANCELLED_WITHOUT_REFUND", response.getCode());

        verify(aiCreditService, never()).refund(any(), any(), anyInt());
        verify(bucket).set(anyString(), any(Duration.class));
        verify(lock).unlock();
    }

    @Test
    @DisplayName("cancelJob - Từ chối khi tác vụ đã COMPLETED: Không cho phép hủy")
    void cancelJob_Completed_CannotCancel() throws Exception {
        String jobId = "job-done-1";
        Long userId = 10L;

        AiJobResultResponse mockJob = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(userId)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.COMPLETED)
                .reservedCredits(2)
                .build();

        doReturn(lock).when(redissonClient).getLock("ai:job:lock:" + jobId);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        doReturn(bucket).when(redissonClient).getBucket(anyString(), any(Codec.class));
        when(bucket.isExists()).thenReturn(true);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(mockJob));

        AiJobCancelResponse response =
                aiJobService.cancelJob(jobId, userId, false);

        assertNotNull(response);
        assertEquals(false, response.isCancelled());
        assertEquals(false, response.isRefunded());
        assertEquals(0, response.getRefundedCredits());
        assertEquals(AiJobStatus.COMPLETED, response.getStatus());
        assertEquals("COMPLETED", response.getCode());

        verify(aiCreditService, never()).refund(any(), any(), anyInt());
        verify(lock).unlock();
    }
}
