package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * DocumentParserFactory là Factory quản lý và tự động lựa chọn DocumentParserStrategy phù hợp dựa theo tên tệp tin.
 */
@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParserStrategy> parsers;

    /**
     * Lựa chọn DocumentParserStrategy tương ứng với định dạng tệp.
     *
     * @param filename tên tệp tin tải lên
     * @return DocumentParserStrategy hỗ trợ định dạng tệp đó
     * @throws BadRequestException nếu tên tệp không hợp lệ hoặc không có Strategy hỗ trợ
     */
    public DocumentParserStrategy getParser(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException("Tên tệp không hợp lệ");
        }

        return parsers.stream()
                .filter(parser -> parser.supports(filename))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Chỉ hỗ trợ file .txt, .docx, hoặc .pdf"));
    }
}
