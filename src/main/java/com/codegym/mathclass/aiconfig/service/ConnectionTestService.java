package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.TestConnectionRequest;
import com.codegym.mathclass.aiconfig.dto.response.TestConnectionResponse;

import java.util.List;

public interface ConnectionTestService {

    TestConnectionResponse testConnection(TestConnectionRequest request);

    TestConnectionResponse verifyKey(Long keyId);

    List<String> fetchAvailableModels(Long providerId);
}
