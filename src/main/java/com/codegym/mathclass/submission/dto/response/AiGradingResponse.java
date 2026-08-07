package com.codegym.mathclass.submission.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * MAT-250: Kết quả AI chấm sơ bộ — Điểm dự kiến + Dự thảo nhận xét + Lỗi hình vẽ Canvas.
 *
 * Đây chỉ là DỰ THẢO: backend KHÔNG tự ghi vào score/teacherFeedback của Submission.
 * Giáo viên có toàn quyền chỉnh sửa trước khi gọi PUT /submissions/{id}/grade.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiGradingResponse {

    /** Điểm số dự kiến của AI (đã clamp trong 0..maxScore, lẻ tối đa 1 chữ số) */
    private Double suggestedScore;

    /** Dự thảo lời nhận xét (Markdown + LaTeX) */
    private String draftFeedback;

    /** Danh sách lỗi hình vẽ Canvas (rỗng nếu không có lỗi hoặc bài tập không có hình mẫu) */
    @Builder.Default
    private List<DrawingIssueItem> drawingIssues = new ArrayList<>();

    /**
     * Bài tập có hình vẽ Canvas mẫu để đối chiếu hay không.
     * Được tính server-side từ assignment.content (quyết định, không phụ thuộc AI).
     */
    private Boolean hasCanvasComparison;
}
