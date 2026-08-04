package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.TaskConfigUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.TaskConfigResponse;

public interface TaskConfigService {
    TaskConfigResponse getTaskConfig(String task);
    TaskConfigResponse updateTaskConfig(String task, TaskConfigUpdateRequest request);
}
