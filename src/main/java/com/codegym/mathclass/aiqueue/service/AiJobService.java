package com.codegym.mathclass.aiqueue.service;

import com.codegym.mathclass.aiqueue.dto.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.dto.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.AiJobSubmitResponse;

public interface AiJobService {

    AiJobSubmitResponse submitJob(String taskCode, Long userId, Object payloadDto);

    AiJobResultResponse getJobStatus(String jobId, Long requestingUserId, boolean isAdmin);

    void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage);

    void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage, Integer retryCount);

    AiJobResultResponse getJobInternal(String jobId);
}
