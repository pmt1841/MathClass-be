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

        // Trường hợp JSON Object hoặc Array bị cắt cụt (truncated) do token limit
        if (firstBrace != -1 && (lastBrace == -1 || lastBrace <= firstBrace)) {
            return repairTruncatedJson(clean.substring(firstBrace).trim());
        }
        if (firstBracket != -1 && (lastBracket == -1 || lastBracket <= firstBracket)) {
            return repairTruncatedJson(clean.substring(firstBracket).trim());
        }

        return repairTruncatedJson(clean.trim());
    }

    /**
     * Tự động sửa chữa và đóng các chuỗi JSON bị cắt cụt do chạm giới hạn token của LLM.
     */
    public static String repairTruncatedJson(String json) {
        if (json == null || json.isBlank()) return "{}";
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed;
        }

        // 1. Xóa bỏ dấu gạch chéo ngược lẻ ở cuối chuỗi nếu có
        while (trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }

        // 2. Đếm số ngoặc kép chưa escape để xác định chuỗi string có đang mở hay không
        int quoteCount = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                quoteCount++;
            }
        }
        if (quoteCount % 2 != 0) {
            trimmed = trimmed + "\"";
        }

        // 3. Đếm số ngoặc nhọn / ngoặc vuông mở để tự động đóng
        int openBraces = 0;
        int openBrackets = 0;
        boolean insideString = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '"' && (i == 0 || trimmed.charAt(i - 1) != '\\')) {
                insideString = !insideString;
            } else if (!insideString) {
                if (c == '{') openBraces++;
                else if (c == '}') openBraces--;
                else if (c == '[') openBrackets++;
                else if (c == ']') openBrackets--;
            }
        }

        StringBuilder sb = new StringBuilder(trimmed);
        while (openBrackets > 0) {
            sb.append("]");
            openBrackets--;
        }
        while (openBraces > 0) {
            sb.append("}");
            openBraces--;
        }
        return sb.toString();
    }

    /**
     * Tự động bảo toàn các dấu gạch chéo ngược (\) trong mã công thức toán LaTeX
     * bên trong chuỗi JSON do LLM sinh ra trước khi đưa vào Jackson ObjectMapper.
     * Ngăn chặn Jackson unescape các lệnh như \pi -> pi, \approx -> approx, \frac -> frac...
     */
    public static String escapeLatexBackslashesInJson(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        // Bắt mọi dấu \ đơn lẻ không phải escape sequence hợp lệ của JSON (\", \\, \/, \b, \f, \n, \r, \t, unicode)
        // và escape thành \\ để Jackson parse ra đúng ký tự \ trong chuỗi Java
        return json.replaceAll("(?<!\\\\)\\\\(?!(?:[bfrnt](?![a-zA-Z])|[\"\\\\/]|u[0-9a-fA-F]{4}))", "\\\\\\\\");
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
