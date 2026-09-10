package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import java.util.List;

/**
 * DocumentParseResult DTO đại diện cho kết quả bóc tách tài liệu bài tập (nội dung Markdown và danh sách ảnh nhúng).
 */
public record DocumentParseResult(
        String content,
        List<AssignmentImageResponse> images
) {
}
