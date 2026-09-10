package com.codegym.mathclass.aiqueue.service;

import com.codegym.mathclass.aiqueue.dto.response.AiJobResultResponse;
import com.codegym.mathclass.aiqueue.model.AiJobStatus;
import com.codegym.mathclass.aiqueue.dto.response.AiJobSubmitResponse;

public interface AiJobService {

    AiJobSubmitResponse submitJob(String taskCode, Long userId, Object payloadDto);

    AiJobResultResponse getJobStatus(String jobId, Long requestingUserId, boolean isAdmin);

    void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage);

    void updateJobStatus(String jobId, AiJobStatus status, Object result, String errorMessage, Integer retryCount);
}
