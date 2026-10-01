package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Service chuyên trách các nghiệp vụ xử lý tài liệu và hình ảnh của bài tập:
 * <ul>
 *     <li>Tải lên hình ảnh minh họa bài tập toán.</li>
 *     <li>Trích xuất nội dung văn bản và ảnh từ tệp tài liệu (PDF, DOCX, TXT, ...).</li>
 *     <li>Dọn dẹp hình ảnh bài tập trên dịch vụ lưu trữ.</li>
 * </ul>
 */
public interface AssignmentDocumentService {

    /**
     * Tải lên hình ảnh minh họa cho bài tập/câu hỏi toán.
     *
     * @param file File ảnh tải lên
     * @return {@link AssignmentImageResponse} chứa mã hình ảnh placeholder và URL công khai
     * @throws IOException nếu xảy ra lỗi lưu trữ
     */
    AssignmentImageResponse uploadImageForAssignment(MultipartFile file) throws IOException;

    /**
     * Tải lên tệp tài liệu (.txt, .docx, .pdf, ...) và trích xuất nội dung văn bản cùng hình ảnh đính kèm.
     *
     * @param file File tài liệu cần phân tích
     * @return Map chứa "content" (String) và "images" (List)
     * @throws Exception nếu xảy ra lỗi trong quá trình parse tài liệu
     */
    Map<String, Object> extractTextFromFile(MultipartFile file) throws Exception;

    /**
     * Dọn dẹp danh sách hình ảnh bài tập trên dịch vụ lưu trữ.
     *
     * @param imageUrls Danh sách URL ảnh cần xóa
     */
    void deleteImages(List<String> imageUrls);
}
