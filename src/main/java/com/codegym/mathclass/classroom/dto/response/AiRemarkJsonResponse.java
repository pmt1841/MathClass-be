package com.codegym.mathclass.classroom.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa kết quả giải mã JSON từ phản hồi AI tạo nhận xét tiến độ học sinh.
 */
@Data
@NoArgsConstructor
public class AiRemarkJsonResponse {

    @JsonProperty("strengths")
    private String strengths;

    @JsonProperty("weaknesses")
    private String weaknesses;

    @JsonProperty("generalAssessment")
    private String generalAssessment;
}
