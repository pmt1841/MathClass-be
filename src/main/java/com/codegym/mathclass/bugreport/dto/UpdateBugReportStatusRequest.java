package com.codegym.mathclass.bugreport.dto;

import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBugReportStatusRequest {

    @NotNull(message = "Trạng thái mới không được để trống")
    private BugReportStatus status;
}
