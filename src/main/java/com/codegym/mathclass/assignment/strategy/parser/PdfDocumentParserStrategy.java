package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.assignment.dto.response.DocumentParseResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Collections;

/**
 * PdfDocumentParserStrategy sử dụng Apache PDFBox bóc tách văn bản từ tệp PDF (.pdf) và chuẩn hóa định dạng Markdown.
 */
@Component
public class PdfDocumentParserStrategy implements DocumentParserStrategy {

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public DocumentParseResult parse(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String content = stripper.getText(document);

            if (content != null) {
                content = content.replaceAll("\\r\\n?", "\n");
                content = content.replaceAll("\\n+", "\n\n");
            }

            return new DocumentParseResult(content != null ? content : "", Collections.emptyList());
        }
    }
}
