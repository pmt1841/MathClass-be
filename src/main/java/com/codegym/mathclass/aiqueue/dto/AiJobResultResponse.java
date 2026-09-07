package com.codegym.mathclass.aiqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobResultResponse {

    private String jobId;
    private Long userId;
    private String taskCode;
    private AiJobStatus status;
    private Object result;
    private String errorMessage;
    private int retryCount;
    private Instant createdAt;
    private Instant completedAt;
}
