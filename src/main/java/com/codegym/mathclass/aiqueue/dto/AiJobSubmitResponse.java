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
public class AiJobSubmitResponse {

    private String jobId;
    private String taskCode;
    private AiJobStatus status;
    private Instant createdAt;
    private String message;
}
