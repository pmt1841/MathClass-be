package com.codegym.mathclass.classroom.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRequestRequest {
    @NotBlank(message = "Mã lớp không được để trống")
    private String classCode;
}
