package com.codegym.mathclass.assignment.strategy.parser;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DocxDocumentParserStrategy sử dụng Apache POI bóc tách tài liệu Word (.docx) sang Markdown và tự động upload ảnh nhúng qua StorageService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocxDocumentParserStrategy implements DocumentParserStrategy {

    private final StorageService storageService;

    @Override
    public boolean supports(String filename) {
        return filename != null && filename.toLowerCase().endsWith(".docx");
    }

    @Override
    public DocumentParseResult parse(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {
            List<AssignmentImageResponse> extractedImages = new ArrayList<>();
            String markdownContent = convertDocxToMarkdown(document, extractedImages);
            return new DocumentParseResult(markdownContent, extractedImages);
        }
    }

    private String convertDocxToMarkdown(XWPFDocument document, List<AssignmentImageResponse> extractedImages) {
        StringBuilder md = new StringBuilder();
        for (IBodyElement element : document.getBodyElements()) {
            if (element instanceof XWPFParagraph) {
                md.append(processParagraph((XWPFParagraph) element, extractedImages));
            } else if (element instanceof XWPFTable) {
                md.append(processTable((XWPFTable) element, extractedImages));
            }
        }
        return md.toString().trim();
    }

    private String processParagraph(XWPFParagraph p, List<AssignmentImageResponse> extractedImages) {
        if (p.isEmpty() || (p.getText().trim().isEmpty()
                && p.getRuns().stream().noneMatch(r -> !r.getEmbeddedPictures().isEmpty()))) {
            return "\n";
        }

        String style = p.getStyleID();
        String prefix = "";
        if (style != null) {
            if (style.contains("Heading1") || "1".equals(style)) prefix = "# ";
            else if (style.contains("Heading2") || "2".equals(style)) prefix = "## ";
            else if (style.contains("Heading3") || "3".equals(style)) prefix = "### ";
            else if (style.contains("Heading4") || "4".equals(style)) prefix = "#### ";
            else if (style.contains("Heading5") || "5".equals(style)) prefix = "##### ";
            else if (style.contains("Heading6") || "6".equals(style)) prefix = "###### ";
        }

        String listPrefix = "";
        if (p.getNumID() != null) {
            int level = p.getNumIlvl() != null ? p.getNumIlvl().intValue() : 0;
            listPrefix = "  ".repeat(level) + "- ";
        }

        StringBuilder paraMd = new StringBuilder();
        for (XWPFRun run : p.getRuns()) {
            String runText = run.text();
            if (runText != null && !runText.isEmpty()) {
                boolean bold = run.isBold();
                boolean italic = run.isItalic();

                runText = runText.replace("\n", " ");

                if (bold || italic) {
                    StringBuilder leadingSpaces = new StringBuilder();
                    while (runText.startsWith(" ")) {
                        leadingSpaces.append(" ");
                        runText = runText.substring(1);
                    }
                    StringBuilder trailingSpaces = new StringBuilder();
                    while (runText.endsWith(" ")) {
                        trailingSpaces.append(" ");
                        runText = runText.substring(0, runText.length() - 1);
                    }

                    paraMd.append(leadingSpaces);
                    if (!runText.isEmpty()) {
                        if (bold && italic) paraMd.append("***").append(runText).append("***");
                        else if (bold) paraMd.append("**").append(runText).append("**");
                        else if (italic) paraMd.append("*").append(runText).append("*");
                    }
                    paraMd.append(trailingSpaces);
                } else {
                    paraMd.append(runText);
                }
            }

            // Processing embedded pictures
            List<XWPFPicture> pictures = run.getEmbeddedPictures();
            if (pictures != null && !pictures.isEmpty()) {
                for (XWPFPicture pic : pictures) {
                    try {
                        XWPFPictureData picData = pic.getPictureData();
                        byte[] byteData = picData.getData();
                        String ext = picData.suggestFileExtension();
                        String originalName = picData.getFileName();
                        if (originalName == null || originalName.isEmpty()) {
                            originalName = "image." + ext;
                        }
                        String contentType = picData.getPackagePart().getContentType();

                        // Upload via StorageService with ASSIGNMENT_IMAGE policy
                        String publicUrl = storageService.upload(byteData, originalName, contentType, StoragePolicy.ASSIGNMENT_IMAGE);
                        String imageCode = "[IMAGE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";

                        extractedImages.add(new AssignmentImageResponse(imageCode, publicUrl));
                        paraMd.append(" ").append(imageCode).append(" ");
                    } catch (Exception e) {
                        log.warn("Failed to extract and upload image from DOCX: {}", e.getMessage(), e);
                    }
                }
            }
        }

        if (!prefix.isEmpty()) {
            return prefix + paraMd.toString() + "\n\n";
        } else if (!listPrefix.isEmpty()) {
            return listPrefix + paraMd.toString() + "\n";
        } else {
            return paraMd.toString() + "\n\n";
        }
    }

    private String processTable(XWPFTable table, List<AssignmentImageResponse> extractedImages) {
        StringBuilder tableMd = new StringBuilder("\n");
        int rowIndex = 0;
        for (XWPFTableRow row : table.getRows()) {
            tableMd.append("|");
            for (XWPFTableCell cell : row.getTableCells()) {
                StringBuilder cellContent = new StringBuilder();
                for (IBodyElement element : cell.getBodyElements()) {
                    if (element instanceof XWPFParagraph) {
                        String pText = processParagraph((XWPFParagraph) element, extractedImages).trim();
                        if (!pText.isEmpty()) {
                            if (cellContent.length() > 0) cellContent.append("<br>");
                            cellContent.append(pText);
                        }
                    } else if (element instanceof XWPFTable) {
                        cellContent.append("[Nested Table]");
                    }
                }
                tableMd.append(" ").append(cellContent.toString().replace("|", "\\|")).append(" |");
            }
            tableMd.append("\n");

            if (rowIndex == 0) {
                tableMd.append("|");
                for (int i = 0; i < row.getTableCells().size(); i++) {
                    tableMd.append("---|");
                }
                tableMd.append("\n");
            }
            rowIndex++;
        }
        return tableMd.toString() + "\n";
    }
}
