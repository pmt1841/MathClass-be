package com.codegym.mathclass.submission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubmissionCommentRequest {

    private String quoteText;

    private Integer occurrenceIndex;

    private String imageCode;

    @NotBlank(message = "Nội dung nhận xét không được để trống")
    private String content;
}
