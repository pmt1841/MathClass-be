package com.codegym.mathclass.assignment.service.helper;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentDrawing;
import com.codegym.mathclass.assignment.entity.AssignmentImage;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import com.codegym.mathclass.assignment.service.TagService;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AssignmentCloneHelper Unit Tests")
class AssignmentCloneHelperTest {

    @Mock
    private TagService tagService;

    @InjectMocks
    private AssignmentCloneHelper cloneHelper;

    private User originalTeacher;
    private User newTeacher;
    private User rootAuthor;
    private Classroom classroom;
    private Assignment originalAssignment;
    private LocalDateTime deadline;

    @BeforeEach
    void setUp() {
        originalTeacher = new User();
        originalTeacher.setId(1L);
        originalTeacher.setFullName("Thầy Nguyễn Văn A");

        newTeacher = new User();
        newTeacher.setId(2L);
        newTeacher.setFullName("Cô Trần Thị B");

        rootAuthor = new User();
        rootAuthor.setId(99L);
        rootAuthor.setFullName("Tác giả Gốc");

        classroom = new Classroom();
        classroom.setId(10L);
        classroom.setClassCode("LOPHOC10A");

        deadline = LocalDateTime.of(2026, 10, 15, 23, 59, 59);

        AssignmentDrawing drawing = AssignmentDrawing.builder()
                .shapeCode("TRIANGLE_ABC")
                .jsxGraphData(Map.of("type", "polygon", "vertices", 3))
                .build();
        drawing.setId(101L);

        AssignmentImage image = new AssignmentImage();
        image.setId(201L);
        image.setImageCode("IMG_01");
        image.setImageUrl("https://example.com/images/math.png");

        originalAssignment = Assignment.builder()
                .title("Bài toán hình học không gian")
                .description("Mô tả bài toán tứ diện")
                .content("<p>Nội dung câu hỏi</p>")
                .status(AssignmentStatus.DRAFT)
                .visibility(AssignmentVisibility.PUBLIC)
                .teacher(originalTeacher)
                .maxScore(10.0)
                .allowResubmit(true)
                .drawings(new ArrayList<>(List.of(drawing)))
                .images(new ArrayList<>(List.of(image)))
                .build();
        originalAssignment.setId(1001L);

        drawing.setAssignment(originalAssignment);
        image.setAssignment(originalAssignment);
    }

    @Nested
    @DisplayName("Kịch bản 1: cloneForClassroom (Giao bài tập đơn lẻ cho lớp)")
    class CloneForClassroomTests {

        @Test
        @DisplayName("Nên sao chép đầy đủ nội dung, bảo toàn allowResubmit, maxScore và liên kết lớp học")
        void cloneForClassroom_Success() {
            Assignment clone = cloneHelper.cloneForClassroom(originalAssignment, classroom, deadline);

            assertNotNull(clone);
            assertEquals("Bài toán hình học không gian", clone.getTitle());
            assertEquals("Mô tả bài toán tứ diện", clone.getDescription());
            assertEquals("<p>Nội dung câu hỏi</p>", clone.getContent());
            assertEquals(originalTeacher, clone.getTeacher());
            assertEquals(classroom, clone.getClassroom());
            assertEquals(originalAssignment.getId(), clone.getParentId());
            assertEquals(deadline, clone.getDeadline());
            assertEquals(AssignmentStatus.PUBLISHED, clone.getStatus());
            assertTrue(clone.isAllowResubmit(), "Phải bảo toàn allowResubmit từ bài gốc");
            assertEquals(10.0, clone.getMaxScore(), "Phải kế thừa maxScore từ bài gốc");
            assertNull(clone.getOriginalAuthor(), "Học sinh không cần thấy originalAuthor");

            // Deep-copy kiểm tra
            assertEquals(1, clone.getDrawings().size());
            AssignmentDrawing cloneDrawing = clone.getDrawings().get(0);
            assertEquals(0L, cloneDrawing.getId(), "Bản clone drawing chưa có ID (mặc định là 0)");
            assertEquals("TRIANGLE_ABC", cloneDrawing.getShapeCode());
            assertEquals(clone, cloneDrawing.getAssignment(), "Drawing phải trỏ về Assignment clone");

            assertEquals(1, clone.getImages().size());
            AssignmentImage cloneImage = clone.getImages().get(0);
            assertEquals(0L, cloneImage.getId(), "Bản clone image chưa có ID (mặc định là 0)");
            assertEquals("IMG_01", cloneImage.getImageCode());
            assertEquals("https://example.com/images/math.png", cloneImage.getImageUrl());
            assertEquals(clone, cloneImage.getAssignment(), "Image phải trỏ về Assignment clone");

            verify(tagService).copyTags(originalAssignment, clone);
        }

        @Test
        @DisplayName("Nên xử lý an toàn khi original drawings và images rỗng hoặc null")
        void cloneForClassroom_EmptyDrawingsAndImages() {
            originalAssignment.setDrawings(null);
            originalAssignment.setImages(new ArrayList<>());
            originalAssignment.setAllowResubmit(false);

            Assignment clone = cloneHelper.cloneForClassroom(originalAssignment, classroom, deadline);

            assertNotNull(clone);
            assertFalse(clone.isAllowResubmit());
            assertNotNull(clone.getDrawings());
            assertTrue(clone.getDrawings().isEmpty());
            assertNotNull(clone.getImages());
            assertTrue(clone.getImages().isEmpty());
            verify(tagService).copyTags(originalAssignment, clone);
        }
    }

    @Nested
    @DisplayName("Kịch bản 2: cloneFromLibrary (Sao chép bài từ Thư viện về Kho cá nhân)")
    class CloneFromLibraryTests {

        @Test
        @DisplayName("Nên tạo bản nháp DRAFT, PRIVATE, ghi nhận originalAuthor là teacher gốc khi chưa có rootAuthor")
        void cloneFromLibrary_OriginalAuthorFallbackToTeacher() {
            originalAssignment.setOriginalAuthor(null);

            Assignment clone = cloneHelper.cloneFromLibrary(originalAssignment, newTeacher);

            assertNotNull(clone);
            assertEquals("Bài toán hình học không gian", clone.getTitle());
            assertEquals(AssignmentStatus.DRAFT, clone.getStatus());
            assertEquals(AssignmentVisibility.PRIVATE, clone.getVisibility());
            assertEquals(newTeacher, clone.getTeacher(), "Giáo viên sở hữu phải là giáo viên clone");
            assertEquals(originalTeacher, clone.getOriginalAuthor(), "OriginalAuthor phải fallback về original.teacher");
            assertNull(clone.getClassroom());
            assertNull(clone.getParentId(), "Kho cá nhân không có parentId");
            assertNull(clone.getDeadline());
            assertFalse(clone.isAllowResubmit(), "Bản nháp kho cá nhân không bật allowResubmit");
            assertEquals(10.0, clone.getMaxScore());

            assertEquals(1, clone.getDrawings().size());
            assertEquals(1, clone.getImages().size());
            verify(tagService).copyTags(originalAssignment, clone);
        }

        @Test
        @DisplayName("Nên giữ nguyên originalAuthor nếu bài gốc vốn đã được clone từ tác giả khác")
        void cloneFromLibrary_PreserveExistingOriginalAuthor() {
            originalAssignment.setOriginalAuthor(rootAuthor);

            Assignment clone = cloneHelper.cloneFromLibrary(originalAssignment, newTeacher);

            assertNotNull(clone);
            assertEquals(rootAuthor, clone.getOriginalAuthor(), "Phải giữ nguyên rootAuthor ban đầu");
            assertEquals(newTeacher, clone.getTeacher());
            verify(tagService).copyTags(originalAssignment, clone);
        }
    }

    @Nested
    @DisplayName("Kịch bản 3: cloneForSheet (Clone câu hỏi vào Phiếu bài tập)")
    class CloneForSheetTests {

        @Test
        @DisplayName("Nên ưu tiên maxScore truyền vào nếu khác null, allowResubmit luôn là false")
        void cloneForSheet_ExplicitMaxScore() {
            Double sheetItemScore = 2.5;

            Assignment clone = cloneHelper.cloneForSheet(originalAssignment, newTeacher, classroom, deadline, sheetItemScore);

            assertNotNull(clone);
            assertEquals(AssignmentStatus.PUBLISHED, clone.getStatus());
            assertEquals(newTeacher, clone.getTeacher());
            assertEquals(classroom, clone.getClassroom());
            assertEquals(deadline, clone.getDeadline());
            assertEquals(originalAssignment.getId(), clone.getParentId());
            assertFalse(clone.isAllowResubmit(), "Phiếu bài tập không cho nộp lại ở cấp độ câu hỏi lẻ");
            assertEquals(2.5, clone.getMaxScore(), "Phải dùng maxScore của sheet item");
            assertNull(clone.getOriginalAuthor());

            assertEquals(1, clone.getDrawings().size());
            assertEquals(1, clone.getImages().size());
            verify(tagService).copyTags(originalAssignment, clone);
        }

        @Test
        @DisplayName("Nên fallback về original.maxScore nếu sheet item maxScore là null")
        void cloneForSheet_FallbackMaxScore() {
            Assignment clone = cloneHelper.cloneForSheet(originalAssignment, newTeacher, null, null, null);

            assertNotNull(clone);
            assertNull(clone.getClassroom());
            assertNull(clone.getDeadline());
            assertEquals(10.0, clone.getMaxScore(), "Fallback về điểm bài gốc");
            verify(tagService).copyTags(originalAssignment, clone);
        }
    }

    @Nested
    @DisplayName("Kịch bản 4: cloneSheetItemFromLibrary (Clone câu hỏi khi clone cả sheet từ Thư viện)")
    class CloneSheetItemFromLibraryTests {

        @Test
        @DisplayName("Nên tạo câu hỏi DRAFT, PRIVATE, độc lập như khi clone bài đơn lẻ từ Thư viện")
        void cloneSheetItemFromLibrary_Success() {
            originalAssignment.setOriginalAuthor(rootAuthor);

            Assignment clone = cloneHelper.cloneSheetItemFromLibrary(originalAssignment, newTeacher);

            assertNotNull(clone);
            assertEquals(AssignmentStatus.DRAFT, clone.getStatus());
            assertEquals(AssignmentVisibility.PRIVATE, clone.getVisibility());
            assertEquals(newTeacher, clone.getTeacher());
            assertEquals(rootAuthor, clone.getOriginalAuthor());
            assertNull(clone.getClassroom());
            assertNull(clone.getParentId());
            assertNull(clone.getDeadline());
            assertFalse(clone.isAllowResubmit());
            assertEquals(10.0, clone.getMaxScore());
            verify(tagService).copyTags(originalAssignment, clone);
        }
    }
}
