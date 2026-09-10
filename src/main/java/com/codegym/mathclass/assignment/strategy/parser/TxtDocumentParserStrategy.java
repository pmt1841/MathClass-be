package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * TxtDocumentParserStrategy bóc tách nội dung văn bản thuần UTF-8 từ tệp có đuôi .txt
 */
@Component
public class TxtDocumentParserStrategy implements DocumentParserStrategy {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".txt");
    }

    @Override
    public DocumentParseResult parse(MultipartFile file) throws Exception {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return new DocumentParseResult(content, Collections.emptyList());
    }
}
