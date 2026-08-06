package com.codegym.mathclass.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintHistoryResponse {
    private Long submissionId;
    private Integer totalUsed;
    private Integer maxHints;
    private Integer remainingHints;
    private List<SubmissionHintItemDTO> hints;
}
