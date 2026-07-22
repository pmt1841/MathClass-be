package com.codegym.mathclass.classroom.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateClassroomRequest {
    
    @NotBlank(message = "Tên lớp không được để trống")
    private String className;
    
    private String description;
    
    @NotNull(message = "Sĩ số tối đa không được để trống")
    @Min(value = 1, message = "Số học sinh tối đa phải lớn hơn 0")
    private Integer maxStudents;
}
