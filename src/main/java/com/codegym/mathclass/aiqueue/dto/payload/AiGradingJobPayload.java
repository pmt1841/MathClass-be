package com.codegym.mathclass.aiqueue.dto.payload;

import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiGradingJobPayload {

    private Long submissionId;
    private AiGradingRequest request;
    private Long teacherId;
}
