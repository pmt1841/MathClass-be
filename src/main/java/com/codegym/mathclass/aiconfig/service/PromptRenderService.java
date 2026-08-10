package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest;
import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;

public interface PromptRenderService {
    RenderPromptResponse renderPrompt(RenderPromptRequest request);
}
