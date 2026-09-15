package com.codegym.mathclass.aiqueue.dto.response;

import com.codegym.mathclass.aiqueue.model.AiJobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJobCancelResponse {

    private String jobId;
    private AiJobStatus status;
    private boolean cancelled;
    private boolean refunded;
    private int refundedCredits;
    private String code;
    private String message;
}
