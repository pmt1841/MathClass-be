package com.codegym.mathclass.assignment.service.helper;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.assignment.repository.SheetPublishedClassProjection;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SheetEnrichmentHelper Unit Tests")
class SheetEnrichmentHelperTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentSheetRepository assignmentSheetRepository;

    @InjectMocks
    private SheetEnrichmentHelper enrichmentHelper;

    private LocalDateTime time1;
    private LocalDateTime time2;

    @BeforeEach
    void setUp() {
        time1 = LocalDateTime.of(2026, 10, 1, 8, 0);
        time2 = LocalDateTime.of(2026, 10, 1, 9, 30);
    }

    private AssignmentResponse createAssignmentResponse(long id, String title) {
        AssignmentResponse response = new AssignmentResponse();
        response.setId(id);
        response.setTitle(title);
        return response;
    }

    private AssignmentSheetResponse createSheetResponse(long id, String title, List<AssignmentResponse> items) {
        AssignmentSheetResponse response = new AssignmentSheetResponse();
        response.setId(id);
        response.setTitle(title);
        response.setItems(items != null ? new ArrayList<>(items) : new ArrayList<>());
        return response;
    }

    @Nested
    @DisplayName("enrichPageForStudent Tests")
    class EnrichPageForStudentTests {

        @Test
        @DisplayName("Nên làm giàu trạng thái nộp bài và thời gian cho học sinh khi có bài nộp")
        void enrichPageForStudent_WithSubmissions_Success() {
            long studentId = 100L;

            AssignmentResponse item1 = createAssignmentResponse(1L, "Câu 1");
            AssignmentResponse item2 = createAssignmentResponse(2L, "Câu 2");

            AssignmentSheetResponse sheet = createSheetResponse(10L, "Phiếu số 1", List.of(item1, item2));
            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(sheet));

            Assignment asgn1 = new Assignment();
            asgn1.setId(1L);
            Assignment asgn2 = new Assignment();
            asgn2.setId(2L);

            Submission sub1 = new Submission();
            sub1.setAssignment(asgn1);
            sub1.setStatus(SubmissionStatus.GRADED);
            sub1.setCreatedAt(time1);
            sub1.setUpdatedAt(time1.plusHours(1));
            sub1.setScore(9.0);

            Submission sub2 = new Submission();
            sub2.setAssignment(asgn2);
            sub2.setStatus(SubmissionStatus.GRADED);
            sub2.setCreatedAt(time2);
            sub2.setUpdatedAt(time2.plusHours(1));
            sub2.setScore(8.5);

            when(submissionRepository.findAllByAssignmentIdInAndStudentId(List.of(1L, 2L), studentId))
                    .thenReturn(List.of(sub1, sub2));

            enrichmentHelper.enrichPageForStudent(page, studentId);

            // Kiểm tra item1
            assertEquals(SubmissionStatus.GRADED.name(), item1.getSubmissionStatus());
            assertEquals(9.0, item1.getSubmissionScore());
            assertEquals(time1, item1.getSubmissionCreatedAt());

            // Kiểm tra item2
            assertEquals(SubmissionStatus.GRADED.name(), item2.getSubmissionStatus());
            assertEquals(8.5, item2.getSubmissionScore());
            assertEquals(time2, item2.getSubmissionCreatedAt());

            // Kiểm tra sheet level status & times
            assertEquals(SubmissionStatus.GRADED.name(), sheet.getSubmissionStatus());
            assertEquals(time2, sheet.getSubmissionCreatedAt());
            assertEquals(time2.plusHours(1), sheet.getSubmissionUpdatedAt());
        }

        @Test
        @DisplayName("Nên tính trạng thái SUBMITTED nếu chỉ có 1 câu đã nộp hoặc đang chờ chấm")
        void enrichPageForStudent_PartialSubmitted_SetsSubmittedStatus() {
            long studentId = 100L;

            AssignmentResponse item1 = createAssignmentResponse(1L, "Câu 1");
            AssignmentResponse item2 = createAssignmentResponse(2L, "Câu 2");

            AssignmentSheetResponse sheet = createSheetResponse(10L, "Phiếu số 1", List.of(item1, item2));
            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(sheet));

            Assignment asgn1 = new Assignment();
            asgn1.setId(1L);

            Submission sub1 = new Submission();
            sub1.setAssignment(asgn1);
            sub1.setStatus(SubmissionStatus.SUBMITTED);
            sub1.setCreatedAt(time1);
            sub1.setUpdatedAt(time1);

            when(submissionRepository.findAllByAssignmentIdInAndStudentId(List.of(1L, 2L), studentId))
                    .thenReturn(List.of(sub1));

            enrichmentHelper.enrichPageForStudent(page, studentId);

            assertEquals(SubmissionStatus.SUBMITTED.name(), sheet.getSubmissionStatus());
            assertNull(sheet.getSubmissionUpdatedAt(), "Chưa chấm điểm nên updatedAt cấp sheet là null");
        }

        @Test
        @DisplayName("Nên bỏ qua an toàn khi danh sách trang rỗng hoặc không có items")
        void enrichPageForStudent_EmptyItems_DoesNothing() {
            AssignmentSheetResponse sheet = createSheetResponse(10L, "Phiếu rỗng", List.of());
            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(sheet));

            enrichmentHelper.enrichPageForStudent(page, 100L);

            verifyNoInteractions(submissionRepository);
            assertNull(sheet.getSubmissionStatus());
        }
    }

    @Nested
    @DisplayName("enrichPageForTeacher Tests")
    class EnrichPageForTeacherTests {

        @Test
        @DisplayName("Nên làm giàu danh sách mã lớp đã giao cho phiếu bài tập của giáo viên")
        void enrichPageForTeacher_Success() {
            long teacherId = 50L;

            AssignmentSheetResponse sheet1 = createSheetResponse(1L, "Phiếu Đại số 10", null);
            AssignmentSheetResponse sheet2 = createSheetResponse(2L, "Phiếu Hình học 10", null);

            Page<AssignmentSheetResponse> page = new PageImpl<>(List.of(sheet1, sheet2));

            SheetPublishedClassProjection row1 = mock(SheetPublishedClassProjection.class);
            when(row1.getTitle()).thenReturn("Phiếu Đại số 10");
            when(row1.getClassCode()).thenReturn("10A1");

            SheetPublishedClassProjection row2 = mock(SheetPublishedClassProjection.class);
            when(row2.getTitle()).thenReturn("Phiếu Đại số 10");
            when(row2.getClassCode()).thenReturn("10A2");

            when(assignmentSheetRepository.findTitleAndClassCodeByTeacherIdAndTitlesIn(
                    eq(teacherId), anyList()))
                    .thenReturn(List.of(row1, row2));

            enrichmentHelper.enrichPageForTeacher(page, teacherId);

            assertEquals(List.of("10A1", "10A2"), sheet1.getPublishedClassCodes());
            assertTrue(sheet2.getPublishedClassCodes().isEmpty());
        }

        @Test
        @DisplayName("Nên bỏ qua khi trang rỗng")
        void enrichPageForTeacher_EmptyPage_DoesNothing() {
            Page<AssignmentSheetResponse> emptyPage = new PageImpl<>(List.of());

            enrichmentHelper.enrichPageForTeacher(emptyPage, 50L);

            verifyNoInteractions(assignmentSheetRepository);
        }
    }
}
