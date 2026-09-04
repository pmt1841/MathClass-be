package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluateRequest;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluationResponse;

public interface StudentRemarkAiService {

    /**
     * Phân tích lịch sử giao bài & nộp bài của học sinh trong khoảng thời gian đã chọn
     * và tạo đánh giá chi tiết (Điểm mạnh, Điểm yếu, Đánh giá chung kèm tỷ lệ hoàn thành X/Y).
     */
    AiStudentRemarkEvaluationResponse evaluateStudentProgress(
            String classCode,
            Long studentId,
            Long currentUserId,
            AiStudentRemarkEvaluateRequest request
    );

    AiStudentRemarkEvaluationResponse evaluateStudentProgress(
            String classCode,
            Long studentId,
            Long currentUserId,
            AiStudentRemarkEvaluateRequest request,
            boolean chargeCredits
    );
}
