package com.codegym.mathclass.aiqueue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String jobId;
    private Long userId;
    private String taskCode;
    private String payloadJson;
    private int retryCount;
    private int reservedCredits;
    private Instant createdAt;
}
