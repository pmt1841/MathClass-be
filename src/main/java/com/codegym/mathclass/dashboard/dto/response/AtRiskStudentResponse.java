package com.codegym.mathclass.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AtRiskStudentResponse {
    private Long id;
    private String name;
    private String className;
    private String issueType;
    private String detail;
    private String avatar;
}
