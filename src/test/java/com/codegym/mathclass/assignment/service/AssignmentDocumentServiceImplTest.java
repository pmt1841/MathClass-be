package com.codegym.mathclass.assignment.service;

import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.service.impl.AssignmentDocumentServiceImpl;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParseResult;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserFactory;
import com.codegym.mathclass.assignment.strategy.parser.DocumentParserStrategy;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentDocumentServiceImpl Unit Tests")
class AssignmentDocumentServiceImplTest {

    @Mock
    private StorageService storageService;

    @Mock
    private DocumentParserFactory documentParserFactory;

    @InjectMocks
    private AssignmentDocumentServiceImpl documentService;

    @Test
    @DisplayName("uploadImageForAssignment nên tải lên ảnh và sinh imageCode hợp lệ")
    void uploadImageForAssignment_Success() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "math.png", "image/png", "data".getBytes());
        when(storageService.upload(file, StoragePolicy.ASSIGNMENT_IMAGE)).thenReturn("https://storage.example.com/math.png");

        AssignmentImageResponse response = documentService.uploadImageForAssignment(file);

        assertNotNull(response);
        assertEquals("https://storage.example.com/math.png", response.getImageUrl());
        assertTrue(response.getImageCode().startsWith("[IMAGE_"));
        assertTrue(response.getImageCode().endsWith("]"));
        verify(storageService, times(1)).upload(file, StoragePolicy.ASSIGNMENT_IMAGE);
    }

    @Test
    @DisplayName("extractTextFromFile nên tìm đúng parser và trả về kết quả parse")
    void extractTextFromFile_Success() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "data".getBytes());
        DocumentParserStrategy parser = mock(DocumentParserStrategy.class);
        DocumentParseResult parseResult = new DocumentParseResult("Nội dung bài tập", Collections.emptyList());

        when(documentParserFactory.getParser("test.docx")).thenReturn(parser);
        when(parser.parse(file)).thenReturn(parseResult);

        Map<String, Object> result = documentService.extractTextFromFile(file);

        assertNotNull(result);
        assertEquals("Nội dung bài tập", result.get("content"));
        assertEquals(Collections.emptyList(), result.get("images"));
        verify(documentParserFactory).getParser("test.docx");
        verify(parser).parse(file);
    }

    @Test
    @DisplayName("deleteImages nên gọi storageService.delete cho từng url và không ném lỗi nếu một url thất bại")
    void deleteImages_CatchesExceptionSafely() {
        List<String> urls = List.of("https://url1.png", "https://url2.png");
        doThrow(new RuntimeException("Lỗi mạng")).when(storageService).delete("https://url1.png");
        doNothing().when(storageService).delete("https://url2.png");

        assertDoesNotThrow(() -> documentService.deleteImages(urls));

        verify(storageService).delete("https://url1.png");
        verify(storageService).delete("https://url2.png");
    }
}
