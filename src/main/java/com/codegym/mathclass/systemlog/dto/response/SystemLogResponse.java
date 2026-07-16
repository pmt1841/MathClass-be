package com.codegym.mathclass.systemlog.dto.response;

import com.codegym.mathclass.systemlog.entity.SystemLogLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemLogResponse {
    private Long id;
    private LocalDateTime timestamp;
    private String actor;
    private String action;
    private SystemLogLevel level;
}
