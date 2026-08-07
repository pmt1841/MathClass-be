package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.AiGradingRequest;
import com.codegym.mathclass.submission.dto.response.AiGradingResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiGradingServiceImplTest {

    private static final String GRADING_TASK_CODE = "ASSIGNMENT_GRADING";

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @InjectMocks
    private AiGradingServiceImpl aiGradingService;

    private Assignment assignment;
    private Submission submission;
    private User teacher;

    private final long submissionId = 100L;
    private final long teacherId = 1L;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(teacherId);
        teacher.setEmail("teacher@codegym.com");

        User student = new User();
        student.setId(2L);
        student.setEmail("student@codegym.com");

        assignment = new Assignment();
        assignment.setId(10L);
        assignment.setTitle("Hình học không gian");
        assignment.setMaxScore(10.0);
        assignment.setTeacher(teacher);
        assignment.setContent("Cho tứ diện ABCD.\n\n"
                + "<!-- DRAWINGS_DATA_START\n"
                + "[{\"shapeCode\":\"SHAPE_1\",\"jsxGraphData\":{\"boundingbox\":[-5,5,5,-5],\"elements\":[]}}]\n"
                + "DRAWINGS_DATA_END -->");

        submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.SUBMITTED);
        submission.setContent("Bài làm của học sinh.\n\n"
                + "<!-- DRAWINGS_DATA_START\n"
                + "[{\"shapeCode\":\"SHAPE_1\",\"jsxGraphData\":{\"boundingbox\":[-5,5,5,-5],\"elements\":[]}}]\n"
                + "DRAWINGS_DATA_END -->");
    }

    @Nested
    @DisplayName("requestAiGrading Tests")
    class RequestAiGradingTests {

        @Test
        @DisplayName("Should return AI draft with drawing issues and score")
        void requestAiGrading_success_withDrawings() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("{\"suggestedScore\": 8.5, \"draftFeedback\": \"Lời giải đúng hướng.\", "
                            + "\"drawingIssues\": [{\"issue\": \"Thiếu đường cao AH\", \"detail\": \"Cần kẻ AH vuông góc BC\"}]}");

            AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(10L), teacherId);

            assertThat(response).isNotNull();
            assertThat(response.getSuggestedScore()).isEqualTo(8.5);
            assertThat(response.getDraftFeedback()).contains("Lời giải đúng hướng");
            assertThat(response.getHasCanvasComparison()).isTrue();
            assertThat(response.getDrawingIssues()).hasSize(1);
            assertThat(response.getDrawingIssues().get(0).getIssue()).isEqualTo("Thiếu đường cao AH");
            assertThat(response.getDrawingIssues().get(0).getDetail()).isEqualTo("Cần kẻ AH vuông góc BC");
        }

        @Test
        @DisplayName("Should parse AI response wrapped in markdown code fence")
        void requestAiGrading_success_fencedJson() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("```json\n{\"suggestedScore\": 7, \"draftFeedback\": \"Khá tốt\", \"drawingIssues\": []}\n```");

            AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId);

            assertThat(response.getSuggestedScore()).isEqualTo(7.0);
            assertThat(response.getDraftFeedback()).isEqualTo("Khá tốt");
            assertThat(response.getDrawingIssues()).isEmpty();
        }

        @Test
        @DisplayName("Should clamp score to maxScore and round to 1 decimal")
        void requestAiGrading_clampsScore() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("{\"suggestedScore\": 12.55, \"draftFeedback\": \"Quá điểm tối đa\", \"drawingIssues\": []}");

            AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId);

            assertThat(response.getSuggestedScore()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("Should set hasCanvasComparison=false and ignore drawing issues when assignment has no sample drawing")
        void requestAiGrading_noCanvasComparison() {
            assignment.setContent("Chỉ có văn bản, không có hình vẽ mẫu.");
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("{\"suggestedScore\": 6, \"draftFeedback\": \"OK\", "
                            + "\"drawingIssues\": [{\"issue\": \"Hallucinated\", \"detail\": \"x\"}]}");

            AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId);

            assertThat(response.getHasCanvasComparison()).isFalse();
            assertThat(response.getDrawingIssues()).isEmpty();
            assertThat(response.getSuggestedScore()).isEqualTo(6.0);
        }


        @Test
        @DisplayName("Should throw ResourceNotFoundException when submission not found")
        void requestAiGrading_submissionNotFound() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw AccessDeniedException when teacher is not the assignment owner")
        void requestAiGrading_notOwner() {
            User otherTeacher = new User();
            otherTeacher.setId(99L);
            assignment.setTeacher(otherTeacher);
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền chấm bài nộp này");
        }

        @Test
        @DisplayName("Should throw BadRequestException when submission is DRAFT (student has not submitted)")
        void requestAiGrading_draftSubmission() {
            submission.setStatus(SubmissionStatus.DRAFT);
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Học sinh chưa nộp bài");
        }

        @Test
        @DisplayName("Should throw BadRequestException when AI returns invalid text (not JSON)")
        void requestAiGrading_invalidAiResponse() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("Tôi không hiểu bài này lắm.");

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("không đúng định dạng");
        }

        @Test
        @DisplayName("Should retry once when AI returns empty first then succeed on second attempt")
        void requestAiGrading_retriesOnEmptyThenSuccess() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenReturn("")
                    .thenReturn("{\"suggestedScore\": 7.5, \"draftFeedback\": \"Sau khi thử lại\", \"drawingIssues\": []}");

            AiGradingResponse response = aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId);

            assertThat(response.getSuggestedScore()).isEqualTo(7.5);
            assertThat(response.getDraftFeedback()).isEqualTo("Sau khi thử lại");
            verify(aiPromptExecutionService, times(2)).executePrompt(eq(GRADING_TASK_CODE), anyString());
        }

        @Test
        @DisplayName("Should wrap AI service runtime error (e.g. request timed out) into BadRequestException with cause")
        void requestAiGrading_aiRuntimeException_wrappedAsBadRequest() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString()))
                    .thenThrow(new RuntimeException("Dịch vụ AI phản hồi lỗi hoặc gặp sự cố kết nối: request timed out"));

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("request timed out");
        }

        @Test
        @DisplayName("Should throw BadRequestException when AI returns blank response (after retry)")
        void requestAiGrading_blankAiResponse() {
            when(submissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));
            when(aiPromptExecutionService.executePrompt(eq(GRADING_TASK_CODE), anyString())).thenReturn("   ");

            assertThatThrownBy(() -> aiGradingService.requestAiGrading(submissionId, new AiGradingRequest(), teacherId))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("phản hồi rỗng")
                    .hasMessageContaining(GRADING_TASK_CODE);

            verify(aiPromptExecutionService, times(2)).executePrompt(eq(GRADING_TASK_CODE), anyString());
        }
    }
}

