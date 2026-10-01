package com.codegym.mathclass.assignment.service.impl;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.service.AssignmentDocumentService;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserFactory;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserStrategy;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentDocumentServiceImpl implements AssignmentDocumentService {

    private final StorageService storageService;
    private final DocumentParserFactory documentParserFactory;

    @Override
    public AssignmentImageResponse uploadImageForAssignment(MultipartFile file) throws IOException {
        String publicUrl = storageService.upload(file, StoragePolicy.ASSIGNMENT_IMAGE);
        String imageCode = "[IMAGE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "]";
        return new AssignmentImageResponse(imageCode, publicUrl);
    }

    @Override
    public Map<String, Object> extractTextFromFile(MultipartFile file) throws Exception {
        String filename = file != null ? file.getOriginalFilename() : null;
        DocumentParserStrategy parser = documentParserFactory.getParser(filename);
        DocumentParseResult result = parser.parse(file);
        return Map.of("content", result.content(), "images", result.images());
    }

    @Override
    public void deleteImages(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return;
        }
        for (String url : imageUrls) {
            try {
                storageService.delete(url);
            } catch (Exception e) {
                log.warn("Không thể xóa file ảnh lưu trữ tại url: {}", url, e);
            }
        }
    }
}
