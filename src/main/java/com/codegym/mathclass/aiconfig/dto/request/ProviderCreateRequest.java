package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.ProviderProtocol;
import com.codegym.mathclass.aiconfig.entity.ProviderStrategy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderCreateRequest {

    @NotBlank(message = "Mã Provider không được để trống")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Mã Provider chỉ bao gồm chữ hoa, số và dấu gạch dưới")
    private String code;

    @NotBlank(message = "Tên Provider không được để trống")
    private String name;

    @NotBlank(message = "Base URL không được để trống")
    @URL(protocol = "https", message = "Base URL phải là đường dẫn HTTPS hợp lệ")
    private String baseUrl;

    @Builder.Default
    private ProviderProtocol protocol = ProviderProtocol.OPENAI_COMPATIBLE;

    private String authHeaderName;

    private String authHeaderPrefix;

    private String authQueryParam;

    private String healthCheckPath;

    @NotNull(message = "Strategy không được để trống")
    private ProviderStrategy strategy;

    private String apiKey;
}
