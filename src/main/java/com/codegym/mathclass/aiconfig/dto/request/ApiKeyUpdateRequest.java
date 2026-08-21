package com.codegym.mathclass.aiconfig.dto.request;

import com.codegym.mathclass.aiconfig.entity.ApiKeyStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyUpdateRequest {

    @Size(max = 100, message = "Tên Key tối đa 100 ký tự")
    private String name;

    @Min(value = 0, message = "Độ ưu tiên không được âm")
    @Max(value = 1000, message = "Độ ưu tiên tối đa 1000")
    private Integer priority;

    private ApiKeyStatus status;

    private String apiKey;
}
