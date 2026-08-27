package com.codegym.mathclass.classroom.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStudentRemarkRequest {

    @Size(max = 2000, message = "Điểm mạnh không được vượt quá 2000 ký tự")
    private String strengths;

    @Size(max = 2000, message = "Điểm yếu không được vượt quá 2000 ký tự")
    private String weaknesses;

    @Size(max = 2000, message = "Đánh giá chung không được vượt quá 2000 ký tự")
    private String generalAssessment;
}
