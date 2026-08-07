package com.codegym.mathclass.submission.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MAT-250: Một lỗi hình vẽ Canvas phát hiện khi đối chiếu với hình mẫu.
 * Ví dụ: "Thiếu đường cao AH", "Sai góc tại đỉnh B", "Sai tiệm cận đồ thị".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawingIssueItem {

    /** Loại lỗi ngắn gọn, ví dụ: "Thiếu đường cao AH" */
    private String issue;

    /** Chi tiết/giải thích thêm (có thể rỗng) */
    private String detail;
}
