package com.codegym.mathclass.aiconfig.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptCreateRequest {

    @NotBlank(message = "Mã prompt code không được để trống")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Mã prompt code chỉ chứa chữ hoa, số và dấu gạch dưới")
    private String code;

    @NotBlank(message = "Tên prompt không được để trống")
    private String name;

    @NotBlank(message = "Mã task code không được để trống")
    private String taskCode;

    @NotBlank(message = "Nội dung mặc định không được để trống")
    private String defaultContent;

    @NotEmpty(message = "Danh sách biến hợp lệ không được để trống")
    private List<String> allowedVariables;

    private String description;
}
