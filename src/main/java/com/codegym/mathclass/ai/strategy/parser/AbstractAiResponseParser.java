package com.codegym.mathclass.ai.strategy.parser;

import com.codegym.mathclass.ai.strategy.parser.exception.AiParsingException;
import com.codegym.mathclass.utils.AiResponseUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Lớp trừu tượng áp dụng Template Method Pattern triển khai cơ chế bóc tách 2 lớp:
 * - Lớp 1: Jackson ObjectMapper làm sạch Markdown Code Fences, repair truncated JSON và escape KaTeX backslash.
 * - Lớp 2 (Fallback): Regex Extractor dự phòng được định nghĩa tại các subclass cụ thể.
 *
 * @param <T> Kiểu đối tượng DTO nghiệp vụ trả về
 */
@Slf4j
public abstract class AbstractAiResponseParser<T> implements AiResponseParser<T> {

    protected final ObjectMapper objectMapper;
    protected final Class<T> targetClass;

    protected AbstractAiResponseParser(ObjectMapper objectMapper, Class<T> targetClass) {
        if (objectMapper != null) {
            this.objectMapper = objectMapper.copy()
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        } else {
            this.objectMapper = new ObjectMapper()
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)
                    .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        }
        this.targetClass = targetClass;
    }

    @Override
    public T parse(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new AiParsingException("Chuỗi phản hồi từ AI rỗng hoặc null");
        }

        // Bước 1: Làm sạch Markdown Code Fences và Escape ký tự KaTeX backslash
        String cleanedJson = AiResponseUtils.extractCleanJson(rawResponse);
        String safeJson = AiResponseUtils.escapeLatexBackslashesInJson(cleanedJson);

        // Lớp 1: Giải mã JSON chuẩn qua Jackson ObjectMapper
        try {
            T result = parseJsonStandard(safeJson);
            if (result != null) {
                log.debug("[AiResponseParser] Bóc tách JSON Lớp 1 thành công cho class: {}", targetClass.getSimpleName());
                return result;
            }
        } catch (Exception e) {
            log.warn("[AiResponseParser] Lớp 1 (Jackson JSON) thất bại cho {}: {}. Kích hoạt Lớp 2 (Regex Fallback)...",
                    targetClass.getSimpleName(), e.getMessage());
        }

        // Lớp 2: Kích hoạt Regex Extractor dự phòng nếu Lớp 1 thất bại
        try {
            T fallbackResult = parseFallbackRegex(rawResponse);
            if (fallbackResult != null) {
                log.info("[AiResponseParser] Bóc tách Regex Lớp 2 thành công cho class: {}", targetClass.getSimpleName());
                return fallbackResult;
            }
        } catch (Exception e) {
            log.error("[AiResponseParser] Lớp 2 (Regex Fallback) thất bại cho {}: {}", targetClass.getSimpleName(), e.getMessage());
        }

        // Cả 2 lớp bóc tách đều thất bại
        throw new AiParsingException("Không thể bóc tách dữ liệu phản hồi AI cho " + targetClass.getSimpleName());
    }

    /**
     * Bóc tách JSON chuẩn ở Lớp 1 thông qua Jackson ObjectMapper.
     */
    protected T parseJsonStandard(String json) throws Exception {
        return objectMapper.readValue(json, targetClass);
    }

    /**
     * Thuật toán bóc tách Regex dự phòng Lớp 2 do các Concrete Parser tự triển khai.
     *
     * @param rawResponse chuỗi phản hồi thô từ LLM
     * @return đối tượng DTO nếu bóc tách thành công, hoặc null nếu không thể bóc tách
     */
    protected abstract T parseFallbackRegex(String rawResponse);
}
