package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ProcessJoinRequestDto {
    @NotNull(message = "Trạng thái không được để trống")
    private JoinRequestStatus status;
}
