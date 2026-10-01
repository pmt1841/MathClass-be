package com.codegym.mathclass.assignment.mapper;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentSheetMapper Unit Tests")
class AssignmentSheetMapperTest {

    @Mock
    private AssignmentMapper assignmentMapper;

    @InjectMocks
    private AssignmentSheetMapper sheetMapper;

    private User teacher;
    private User author;
    private Classroom classroom;
    private AssignmentSheet masterSheet;
    private AssignmentSheet sheet;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();

        teacher = new User();
        teacher.setId(1L);
        teacher.setFullName("Thầy Nguyễn Văn A");

        author = new User();
        author.setId(99L);
        author.setFullName("Tác giả Gốc");

        classroom = new Classroom();
        classroom.setClassCode("MATH10");
        classroom.setClassName("Lớp 10 Toán");

        masterSheet = new AssignmentSheet();
        masterSheet.setId(500L);

        Assignment activeItem = new Assignment();
        activeItem.setId(10L);
        activeItem.setStatus(AssignmentStatus.PUBLISHED);
        activeItem.setMaxScore(2.5);

        Assignment deletedItem = new Assignment();
        deletedItem.setId(11L);
        deletedItem.setStatus(AssignmentStatus.DELETED);

        sheet = new AssignmentSheet();
        sheet.setId(100L);
        sheet.setTitle("Phiếu ôn tập chương 1");
        sheet.setDescription("Mô tả ôn tập");
        sheet.setDeadline(now.plusDays(3));
        sheet.setVisibility(AssignmentVisibility.PUBLIC);
        sheet.setTeacher(teacher);
        sheet.setOriginalAuthor(author);
        sheet.setClassroom(classroom);
        sheet.setMasterSheet(masterSheet);
        sheet.setItems(new ArrayList<>(List.of(activeItem, deletedItem)));
    }

    @Test
    @DisplayName("toResponse nên ánh xạ đầy đủ thuộc tính và lọc bỏ bài tập DELETED")
    void toResponse_Success() {
        AssignmentResponse mockItemResponse = new AssignmentResponse();
        mockItemResponse.setId(10L);

        when(assignmentMapper.toAssignmentResponseWithoutContent(any(Assignment.class)))
                .thenReturn(mockItemResponse);

        AssignmentSheetResponse response = sheetMapper.toResponse(sheet);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Phiếu ôn tập chương 1", response.getTitle());
        assertEquals("Mô tả ôn tập", response.getDescription());
        assertEquals(sheet.getDeadline(), response.getDeadline());
        assertEquals(AssignmentVisibility.PUBLIC, response.getVisibility());
        assertEquals(1L, response.getTeacherId());
        assertEquals("Thầy Nguyễn Văn A", response.getTeacherName());
        assertEquals(99L, response.getOriginalAuthorId());
        assertEquals("Tác giả Gốc", response.getOriginalAuthorName());
        assertEquals("MATH10", response.getClassCode());
        assertEquals("Lớp 10 Toán", response.getClassName());
        assertEquals(500L, response.getMasterSheetId());

        assertNotNull(response.getItems());
        assertEquals(1, response.getItems().size(), "Phải lọc bỏ bài tập đã DELETED");
        assertEquals(2.5, response.getItems().get(0).getMaxScore());
        verify(assignmentMapper, times(1)).toAssignmentResponseWithoutContent(any(Assignment.class));
    }

    @Test
    @DisplayName("toResponse nên trả về null khi entity null")
    void toResponse_NullEntity_ReturnsNull() {
        assertNull(sheetMapper.toResponse(null));
    }

    @Test
    @DisplayName("toResponseWithoutContent nên trả về response không có items")
    void toResponseWithoutContent_Success() {
        AssignmentSheetResponse response = sheetMapper.toResponseWithoutContent(sheet);

        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals("Phiếu ôn tập chương 1", response.getTitle());
        assertNull(response.getItems(), "toResponseWithoutContent không được chứa items");
    }
}
