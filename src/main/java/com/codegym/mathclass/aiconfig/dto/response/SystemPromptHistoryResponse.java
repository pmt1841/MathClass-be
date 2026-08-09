package com.codegym.mathclass.aiconfig.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemPromptHistoryResponse {
    private Long id;
    private Long promptId;
    private Integer version;
    private String content;
    private String changeReason;
    private String createdBy;
    private LocalDateTime createdAt;
}
