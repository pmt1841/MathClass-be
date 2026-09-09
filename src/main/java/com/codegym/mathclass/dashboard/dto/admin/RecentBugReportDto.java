package com.codegym.mathclass.dashboard.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentBugReportDto {
    private Long id;
    private String reporterEmail;
    private String errorType;
    private String description;
    private String status;
    private LocalDateTime createdAt;
}
