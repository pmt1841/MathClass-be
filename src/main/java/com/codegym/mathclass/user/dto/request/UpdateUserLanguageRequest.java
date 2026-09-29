package com.codegym.mathclass.user.dto.request;

import com.codegym.mathclass.common.annotation.SupportedLocale;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserLanguageRequest {

    @NotBlank(message = "Mã ngôn ngữ không được để trống")
    @SupportedLocale
    private String language;
}
