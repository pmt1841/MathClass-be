package com.codegym.mathclass.assignment.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class GenerateQuestionRequest {

    @NotBlank(message = "Prompt không được để trống")
    @Size(max = 2000, message = "Prompt tối đa 2000 ký tự")
    private String prompt;

    @Min(value = 6, message = "Khối lớp từ 6 đến 12")
    @Max(value = 12, message = "Khối lớp từ 6 đến 12")
    private Integer grade;

    private String difficulty; // NHAN_BIET, THONG_HIEU, VAN_DUNG, VAN_DUNG_CAO

    private String topic;

    private String questionType; // ESSAY, MULTIPLE_CHOICE

    @Builder.Default
    private Boolean includeCanvasDiagram = false;

    @Builder.Default
    private Boolean includeExplanation = false;
}
