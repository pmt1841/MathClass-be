package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.ApiKeyCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyStatusPatchRequest;
import com.codegym.mathclass.aiconfig.dto.request.ApiKeyUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ApiKeyResponse;

import java.util.List;

public interface ApiKeyService {
    List<ApiKeyResponse> getKeysByProviderId(Long providerId);
    ApiKeyResponse addKey(Long providerId, ApiKeyCreateRequest request);
    void deleteKey(Long keyId);
    ApiKeyResponse updateKeyStatus(Long keyId, ApiKeyStatusPatchRequest request);
    ApiKeyResponse updateKey(Long keyId, ApiKeyUpdateRequest request);
}
