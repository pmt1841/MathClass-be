package com.codegym.mathclass.aiqueue.dto.payload;

import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluateRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentRemarkJobPayload {

    private String classCode;
    private Long studentId;
    private Long currentUserId;
    private AiStudentRemarkEvaluateRequest request;
}
