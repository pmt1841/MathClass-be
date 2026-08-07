package com.codegym.mathclass.submission.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MAT-250: Request AI chấm sơ bộ.
 *
 * assignmentId được gửi kèm để thuận tiện cho FE, tuy nhiên backend
 * luôn lấy assignment từ submission (nguồn tin cậy), tránh IDOR.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiGradingRequest {

    private Long assignmentId;
}
