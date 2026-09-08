package com.codegym.mathclass.ai.strategy.parser;

/**
 * Interface Core định nghĩa hợp đồng bóc tách chuỗi phản hồi thô từ AI (LLM)
 * thành đối tượng DTO nghiệp vụ tương ứng.
 *
 * @param <T> Kiểu DTO dữ liệu trả về sau khi bóc tách thành công
 */
public interface AiResponseParser<T> {

    /**
     * Bóc tách chuỗi phản hồi thô từ LLM thành DTO đối tượng T.
     *
     * @param rawResponse chuỗi phản hồi thô từ LLM
     * @return đối tượng DTO đã giải mã thành công
     * @throws com.codegym.mathclass.ai.strategy.parser.exception.AiParsingException nếu cả 2 lớp bóc tách thất bại
     */
    T parse(String rawResponse);

    /**
     * Kiểm tra xem Parser có hỗ trợ loại phản hồi AI chỉ định hay không.
     *
     * @param type loại phản hồi AI (AiResponseType)
     * @return true nếu hỗ trợ, ngược lại false
     */
    boolean supports(AiResponseType type);
}
