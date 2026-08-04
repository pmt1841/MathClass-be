package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestConnectionRequest {

    @NotBlank(message = "Mã Provider (providerCode) không được để trống")
    private String providerCode;

    @NotBlank(message = "API Key không được để trống")
    private String apiKey;

    private String model;

    private String baseUrl;

    private ProviderProtocol protocol;

    private String authHeaderName;

    private String authHeaderPrefix;

    private String authQueryParam;

    private String healthCheckPath;
}
