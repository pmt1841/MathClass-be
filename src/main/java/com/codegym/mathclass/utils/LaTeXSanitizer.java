package com.codegym.mathclass.utils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Tiện ích kiểm tra nội dung LaTeX để ngăn chặn các lệnh nguy hiểm
 * có thể gây ra lỗ hổng bảo mật (ví dụ: đọc file hệ thống, ghi file).
 *
 * Backend chỉ lưu raw text/LaTeX string, frontend tự render bằng KaTeX/MathJax.
 * Class này lọc các từ khóa LaTeX nguy hiểm trước khi lưu vào CSDL.
 */
public class LaTeXSanitizer {

    // Danh sách các lệnh LaTeX nguy hiểm cần chặn.
    // Dùng (?![a-zA-Z]) thay vì \b để bắt cả \write18, \write{...}, v.v.
    // (trong LaTeX, lệnh có thể theo sau bởi số hoặc ký tự đặc biệt)
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("\\\\input(?![a-zA-Z])"),
            Pattern.compile("\\\\include(?![a-zA-Z])"),
            Pattern.compile("\\\\write(?![a-zA-Z])"),
            Pattern.compile("\\\\openin(?![a-zA-Z])"),
            Pattern.compile("\\\\openout(?![a-zA-Z])"),
            Pattern.compile("\\\\read(?![a-zA-Z])"),
            Pattern.compile("\\\\catcode(?![a-zA-Z])"),
            Pattern.compile("\\\\csname(?![a-zA-Z])"),
            Pattern.compile("\\\\expandafter(?![a-zA-Z])"),
            Pattern.compile("\\\\immediate(?![a-zA-Z])"),
            Pattern.compile("\\\\special(?![a-zA-Z])"),
            Pattern.compile("\\\\verbatiminput(?![a-zA-Z])"),
            Pattern.compile("\\\\lstinputlisting(?![a-zA-Z])")
    );

    private LaTeXSanitizer() {
        // Utility class, không khởi tạo
    }

    /**
     * Kiểm tra xem chuỗi LaTeX có an toàn không.
     *
     * @param text nội dung cần kiểm tra
     * @return true nếu an toàn, false nếu phát hiện lệnh nguy hiểm
     */
    public static boolean isSafe(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(text).find()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trả về lệnh nguy hiểm đầu tiên tìm thấy, dùng cho thông báo lỗi rõ ràng.
     *
     * @param text nội dung cần kiểm tra
     * @return tên lệnh nguy hiểm (vd: "\\input"), hoặc null nếu an toàn
     */
    public static String findDangerousCommand(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return null;
    }
}
