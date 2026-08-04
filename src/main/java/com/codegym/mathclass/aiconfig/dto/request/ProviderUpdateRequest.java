package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStatus;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ProviderUpdateRequest {

    @NotBlank(message = "Tên Provider không được để trống")
    private String name;

    @NotBlank(message = "Base URL không được để trống")
    @URL(protocol = "https", message = "Base URL phải là đường dẫn HTTPS hợp lệ")
    private String baseUrl;

    private ProviderProtocol protocol = ProviderProtocol.OPENAI_COMPATIBLE;

    private String authHeaderName;

    private String authHeaderPrefix;

    private String authQueryParam;

    private String healthCheckPath;

    @NotNull(message = "Strategy không được để trống")
    private ProviderStrategy strategy;

    @NotNull(message = "Trạng thái status không được để trống")
    private ProviderStatus status;
}
