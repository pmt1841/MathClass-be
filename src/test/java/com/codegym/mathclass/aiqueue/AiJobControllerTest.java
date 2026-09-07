package com.codegym.mathclass.aiqueue;

import com.codegym.mathclass.aiqueue.controller.AiJobController;
import com.codegym.mathclass.aiqueue.dto.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.service.AiJobService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobControllerTest {

    @Mock
    private AiJobService aiJobService;

    @InjectMocks
    private AiJobController aiJobController;

    @Test
    @DisplayName("getJobStatus - Trả về 200 OK kèm thông tin tiến độ tác vụ AI")
    void getJobStatus_Success() {
        String jobId = "test-job-uuid";
        CustomUserDetails userDetails = new CustomUserDetails(
                5L, "Teacher", "teacher@mathclass.com", "pass", true, null,
                List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
        );

        AiJobResultResponse mockResponse = AiJobResultResponse.builder()
                .jobId(jobId)
                .userId(5L)
                .taskCode("QUESTION_GEN")
                .status(AiJobStatus.COMPLETED)
                .result("Đề thi đã sinh")
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();

        when(aiJobService.getJobStatus(jobId, 5L, false)).thenReturn(mockResponse);

        ResponseEntity<AiJobResultResponse> responseEntity = aiJobController.getJobStatus(jobId, userDetails);

        assertNotNull(responseEntity);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertNotNull(responseEntity.getBody());
        assertEquals(jobId, responseEntity.getBody().getJobId());
        assertEquals(AiJobStatus.COMPLETED, responseEntity.getBody().getStatus());

        verify(aiJobService).getJobStatus(jobId, 5L, false);
    }
}
