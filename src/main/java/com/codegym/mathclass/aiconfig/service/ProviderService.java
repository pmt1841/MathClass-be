package com.codegym.mathclass.aiconfig.service;

import com.codegym.mathclass.aiconfig.dto.request.ProviderCreateRequest;
import com.codegym.mathclass.aiconfig.dto.request.ProviderUpdateRequest;
import com.codegym.mathclass.aiconfig.dto.response.ProviderResponse;
import com.codegym.mathclass.aiconfig.entity.Provider;

import java.util.List;

public interface ProviderService {
    List<ProviderResponse> getAllProviders();
    ProviderResponse getProviderById(Long id);
    ProviderResponse createProvider(ProviderCreateRequest request);
    ProviderResponse updateProvider(Long id, ProviderUpdateRequest request);
    void deleteProvider(Long id);
}
