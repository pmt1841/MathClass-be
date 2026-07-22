package com.codegym.mathclass.classroom.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateClassroomRequest {
    @NotBlank(message = "Tên lớp không được để trống")
    private String name;

    @Min(value = 1, message = "Số học sinh tối đa phải lớn hơn 0")
    private Integer maxStudents;

    private String description;
}
