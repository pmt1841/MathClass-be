package com.codegym.mathclass.utils;

/**
 * Tiện ích xử lý và trích xuất dữ liệu từ chuỗi phản hồi thô của AI (LLM).
 * Giúp loại bỏ markdown code fences, tiền tố/hậu tố văn bản giải thích
 * và bóc tách các định dạng dữ liệu chuẩn như JSON.
 */
public class AiResponseUtils {

    private AiResponseUtils() {
        // Utility class, không khởi tạo
    }

    /**
     * Trích xuất chuỗi JSON sạch từ phản hồi AI:
     * - Loại bỏ markdown code block (```json ... ``` hoặc ``` ... ``` nếu có)
     * - Cắt lấy phần nội dung nằm giữa cặp dấu ngoặc bao ngoài cùng '{'...'}' hoặc '['...']'
     *
     * @param output chuỗi phản hồi thô từ AI
     * @return chuỗi JSON hợp lệ hoặc "{}" / "[]" nếu rỗng
     */
    public static String extractCleanJson(String output) {
        if (output == null || output.isBlank()) {
            return "{}";
        }
        String clean = output.trim();
        if (clean.contains("```")) {
            clean = clean.replaceAll("(?s)^.*?```(?:json)?\\s*", "")
                         .replaceAll("(?s)\\s*```.*$", "");
        }
        int firstBrace = clean.indexOf('{');
        int lastBrace = clean.lastIndexOf('}');
        int firstBracket = clean.indexOf('[');
        int lastBracket = clean.lastIndexOf(']');

        // Trường hợp là JSON Array (bắt đầu trước JSON Object hoặc không có Object)
        if (firstBracket != -1 && lastBracket != -1 && lastBracket > firstBracket
                && (firstBrace == -1 || firstBracket < firstBrace)) {
            return clean.substring(firstBracket, lastBracket + 1).trim();
        }

        // Trường hợp là JSON Object
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            return clean.substring(firstBrace, lastBrace + 1).trim();
        }

        return clean.trim();
    }

    /**
     * Loại bỏ toàn bộ markdown code fence bao ngoài văn bản (vd: ```xml, ```markdown, ```).
     *
     * @param text chuỗi văn bản cần làm sạch
     * @return chuỗi văn bản đã loại bỏ markdown fence
     */
    public static String stripMarkdownFences(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String clean = text.trim();
        if (clean.contains("```")) {
            clean = clean.replaceAll("(?s)^.*?```[a-zA-Z]*\\s*", "")
                         .replaceAll("(?s)\\s*```.*$", "");
        }
        return clean.trim();
    }
}
