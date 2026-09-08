package com.codegym.mathclass.ai.strategy.parser;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Spring Factory quản lý tự động các Strategy Beans triển khai {@link AiResponseParser}
 * và cấp phát Parser phù hợp dựa trên {@link AiResponseType}.
 */
@Component
public class AiResponseParserFactory {

    private final Map<AiResponseType, AiResponseParser<?>> parserMap;

    public AiResponseParserFactory(List<AiResponseParser<?>> parsers) {
        this.parserMap = new EnumMap<>(AiResponseType.class);
        for (AiResponseParser<?> parser : parsers) {
            for (AiResponseType type : AiResponseType.values()) {
                if (parser.supports(type)) {
                    parserMap.put(type, parser);
                    break;
                }
            }
        }
    }

    /**
     * Lấy Strategy Parser phù hợp với loại phản hồi AI chỉ định.
     *
     * @param type loại phản hồi AI (AiResponseType)
     * @param <T> kiểu DTO dữ liệu trả về
     * @return AiResponseParser tương ứng
     * @throws IllegalArgumentException nếu không tìm thấy Parser tương ứng
     */
    @SuppressWarnings("unchecked")
    public <T> AiResponseParser<T> getParser(AiResponseType type) {
        AiResponseParser<?> parser = parserMap.get(type);
        if (parser == null) {
            throw new IllegalArgumentException("Không tìm thấy AiResponseParser phù hợp cho type: " + type);
        }
        return (AiResponseParser<T>) parser;
    }
}
