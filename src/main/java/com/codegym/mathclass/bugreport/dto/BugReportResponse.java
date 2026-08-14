package com.codegym.mathclass.bugreport.dto;

import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugReportResponse {

    private Long id;
    private String reporterEmail;
    private String reporterName;
    private Long userId;
    private BugErrorType errorType;
    private String description;
    private BugReportStatus status;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
