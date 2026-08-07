package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;

/**
 * MAT-250: Dịch vụ AI chấm sơ bộ bài làm của học sinh cho giáo viên.
 */
public interface AiGradingService {

    /**
     * Chạy AI chấm sơ bộ cho một bài nộp.
     *
     * @param submissionId ID bài nộp của học sinh
     * @param request      Request (assignmentId để tiện, không bắt buộc)
     * @param teacherId    ID giáo viên đang thao tác (được kiểm tra quyền sở hữu)
     * @return Dự thảo điểm + nhận xét + lỗi hình vẽ (KHÔNG ghi vào DB)
     */
    AiGradingResponse requestAiGrading(long submissionId, AiGradingRequest request, long teacherId);
}
