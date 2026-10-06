package com.codegym.mathclass.submission.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @Size(max = 10_000_000, message = "Dữ liệu ảnh không được vượt quá 10MB")
    private String imageData;

    private String mimeType;
}
