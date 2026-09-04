package com.codegym.mathclass.aiqueue;

import com.codegym.mathclass.aiconfig.credit.entity.AiCreditConfig;
import com.codegym.mathclass.aiconfig.credit.service.AiCreditService;
import com.codegym.mathclass.aiconfig.entity.TaskConfig;
import com.codegym.mathclass.aiconfig.repository.TaskConfigRepository;
import com.codegym.mathclass.aiqueue.dto.AiJobMessage;
import com.codegym.mathclass.aiqueue.dto.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;
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
}
