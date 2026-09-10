package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.assignment.dto.response.DocumentParseResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * DocumentParserStrategy định nghĩa hợp đồng chuẩn cho các thuật toán bóc tách tài liệu bài tập.
 */
public interface DocumentParserStrategy {

    /**
     * Kiểm tra xem Strategy có hỗ trợ bóc tách file với tên chỉ định hay không.
     *
     * @param filename tên tệp tin cần bóc tách
     * @return true nếu hỗ trợ, ngược lại false
     */
    boolean supports(String filename);

    /**
     * Thực hiện bóc tách nội dung tài liệu và ảnh nhúng từ tệp tin.
     *
     * @param file tệp tin MultipartFile được tải lên
     * @return DocumentParseResult chứa văn bản Markdown và danh sách ảnh nhúng
     * @throws Exception nếu gặp lỗi trong quá trình bóc tách
     */
    DocumentParseResult parse(MultipartFile file) throws Exception;
}
