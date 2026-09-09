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
public class RecentSystemLogResponse {
    private Long id;
    private String actor;
    private String resourceType;
    private String action;
    private String level;
    private LocalDateTime createdAt;
}
