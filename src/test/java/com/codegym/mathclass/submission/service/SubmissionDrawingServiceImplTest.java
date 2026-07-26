package com.codegym.mathclass.submission.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.SubmissionDrawingRequest;
import com.codegym.mathclass.submission.dto.SubmissionDrawingResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionDrawing;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionDrawingRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionDrawingServiceImplTest {

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionDrawingRepository submissionDrawingRepository;

    @InjectMocks
    private SubmissionDrawingServiceImpl drawingService;

    private User teacher;
    private User student;
    private Assignment assignment;
    private Submission submission;
    private SubmissionDrawing drawing;

    private final long submissionId = 100L;
    private final long drawingId = 200L;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(1L);
        teacher.setEmail("teacher@codegym.com");

        student = new User();
        student.setId(2L);
        student.setEmail("student@codegym.com");

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTeacher(teacher);

        submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.DRAFT);

        drawing = SubmissionDrawing.builder()
                .submission(submission)
                .shapeCode("TRIANGLE")
                .jsxGraphData(Map.of("elements", "board_data"))
                .metadata(Map.of("author", "student"))
                .build();
        drawing.setId(drawingId);
    }

    @Nested
    @DisplayName("saveOrUpdateDrawing Tests")
    class SaveOrUpdateDrawingTests {

        @Test
        @DisplayName("Should save or update drawing successfully when submission is draft and user is owner")
        void saveOrUpdateDrawing_ValidRequest_Success() {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            request.setShapeCode("TRIANGLE");
            request.setJsxGraphData(Map.of("elements", "board_data"));

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionDrawingRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(drawing));
            when(submissionDrawingRepository.save(any(SubmissionDrawing.class))).thenReturn(drawing);

            SubmissionDrawingResponse response = drawingService.saveOrUpdateDrawing(submissionId, request, "student@codegym.com");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(drawingId);
            assertThat(response.getShapeCode()).isEqualTo("TRIANGLE");
            verify(submissionDrawingRepository, times(1)).save(any(SubmissionDrawing.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when submission not found")
        void saveOrUpdateDrawing_SubmissionNotFound_ThrowsException() {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            when(submissionRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> drawingService.saveOrUpdateDrawing(999L, request, "student@codegym.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Submission not found with id");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is not submission owner")
        void saveOrUpdateDrawing_NotOwner_ThrowsException() {
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> drawingService.saveOrUpdateDrawing(submissionId, request, "other@codegym.com"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You are not allowed to modify this submission");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when submission is already SUBMITTED")
        void saveOrUpdateDrawing_AlreadySubmitted_ThrowsException() {
            submission.setStatus(SubmissionStatus.SUBMITTED);
            SubmissionDrawingRequest request = new SubmissionDrawingRequest();

            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> drawingService.saveOrUpdateDrawing(submissionId, request, "student@codegym.com"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Submission is already submitted. Please un-submit to edit your drawing.");
        }
    }

    @Nested
    @DisplayName("getDrawingBySubmissionId Tests")
    class GetDrawingBySubmissionIdTests {

        @Test
        @DisplayName("Should return drawing for student owner")
        void getDrawingBySubmissionId_StudentOwner_Success() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionDrawingRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(drawing));

            SubmissionDrawingResponse response = drawingService.getDrawingBySubmissionId(submissionId, "student@codegym.com");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(drawingId);
        }

        @Test
        @DisplayName("Should return drawing for teacher owner")
        void getDrawingBySubmissionId_TeacherOwner_Success() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionDrawingRepository.findBySubmissionId(submissionId)).thenReturn(Optional.of(drawing));

            SubmissionDrawingResponse response = drawingService.getDrawingBySubmissionId(submissionId, "teacher@codegym.com");

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(drawingId);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when user is unauthorized")
        void getDrawingBySubmissionId_Unauthorized_ThrowsException() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> drawingService.getDrawingBySubmissionId(submissionId, "other@codegym.com"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("You are not allowed to view this drawing");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when drawing not found")
        void getDrawingBySubmissionId_DrawingNotFound_ThrowsException() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(submissionDrawingRepository.findBySubmissionId(submissionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> drawingService.getDrawingBySubmissionId(submissionId, "student@codegym.com"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Drawing not found for submission id");
        }
    }
}
