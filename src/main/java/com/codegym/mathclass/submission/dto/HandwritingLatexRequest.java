package com.codegym.mathclass.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HandwritingLatexRequest {

    @NotBlank(message = "Dữ liệu hình ảnh không được để trống")
    @jakarta.validation.constraints.Size(max = 10_000_000, message = "Dữ liệu ảnh không được vượt quá 10MB")
    private String imageData;

    private String mimeType;
}
