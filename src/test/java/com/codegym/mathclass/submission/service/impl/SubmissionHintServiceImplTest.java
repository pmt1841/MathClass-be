package com.codegym.mathclass.submission.service.impl;

import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.HintLimitExceededException;
import com.codegym.mathclass.exception.InvalidSubmissionStateException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.dto.request.StudentHintRequest;
import com.codegym.mathclass.submission.dto.response.HintHistoryResponse;
import com.codegym.mathclass.submission.dto.response.StudentHintResponse;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionHint;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionHintRepository;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.codegym.mathclass.aiconfig.service.PromptRenderService;

import com.codegym.mathclass.aiconfig.dto.response.RenderPromptResponse;

@ExtendWith(MockitoExtension.class)
class SubmissionHintServiceImplTest {

    @Mock
    private SubmissionHintRepository submissionHintRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @Mock
    private PromptRenderService promptRenderService;

    @InjectMocks
    private SubmissionHintServiceImpl submissionHintService;

    private Assignment assignment;
    private User student;
    private User teacher;
    private Submission submission;

    private final long assignmentId = 10L;
    private final long studentId = 2L;
    private final long teacherId = 1L;
    private final long submissionId = 100L;
    private final String studentEmail = "student@codegym.com";

    @BeforeEach
    void setUp() {
        lenient().when(promptRenderService.renderPrompt(any())).thenAnswer(invocation -> {
            com.codegym.mathclass.aiconfig.dto.request.RenderPromptRequest req = invocation.getArgument(0);
            Object studentContent = req.getVariables() != null ? req.getVariables().get("student_content") : "";
            return RenderPromptResponse.builder()
                    .renderedPrompt("Test Socratic prompt rendered. Student content: " + studentContent)
                    .build();
        });

        teacher = new User();
        teacher.setId(teacherId);
        teacher.setFullName("Nguyen Teacher");
        teacher.setEmail("teacher@codegym.com");

        student = new User();
        student.setId(studentId);
        student.setFullName("Tran Student");
        student.setEmail(studentEmail);

        assignment = new Assignment();
        assignment.setId(assignmentId);
        assignment.setTitle("Math Assignment 1");
        assignment.setContent("Solve equations: x^2 - 5x + 6 = 0");
        assignment.setTeacher(teacher);
        assignment.setDeadline(LocalDateTime.now().plusDays(2));

        submission = new Submission();
        submission.setId(submissionId);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setStatus(SubmissionStatus.DRAFT);
        submission.setContent("x^2 - 5x + 6 = 0. Delta = 1.");
    }

    @Nested
    @DisplayName("requestHint Tests")
    class RequestHintTests {

        @Test
        @DisplayName("UT-BE-01: Happy Path - First hint request returns hint and remaining hints count = 2")
        void requestHint_happyPath_firstHint_success() {
            StudentHintRequest request = new StudentHintRequest("Delta = (-5)^2 - 4*1*6 = 1.");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(0);
            when(aiPromptExecutionService.executePrompt(eq("STUDENT_HINT"), anyString(), anyLong()))
                    .thenReturn("Vì Delta = 1 > 0, hãy áp dụng công thức x1, x2 = (-b ± sqrt(Delta)) / 2a.");
            when(submissionHintRepository.save(any(SubmissionHint.class))).thenAnswer(invocation -> {
                SubmissionHint sh = invocation.getArgument(0);
                sh.setId(1L);
                sh.setCreatedAt(LocalDateTime.now());
                return sh;
            });

            StudentHintResponse response = submissionHintService.requestHint(assignmentId, request, studentEmail);

            assertThat(response).isNotNull();
            assertThat(response.getHintNumber()).isEqualTo(1);
            assertThat(response.getMaxHints()).isEqualTo(3);
            assertThat(response.getRemainingHints()).isEqualTo(2);
            assertThat(response.getHintContent()).contains("Vì Delta = 1 > 0");

            verify(submissionHintRepository, times(1)).save(any(SubmissionHint.class));
        }

        @Test
        @DisplayName("UT-BE-02: Should auto create DRAFT Submission when submission does not exist yet")
        void requestHint_submissionNotExists_autoCreateDraftSubmission() {
            StudentHintRequest request = new StudentHintRequest("I just opened the assignment.");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.empty());

            when(submissionRepository.save(any(Submission.class))).thenAnswer(invocation -> {
                Submission s = invocation.getArgument(0);
                s.setId(submissionId);
                return s;
            });
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(0);
            when(aiPromptExecutionService.executePrompt(eq("STUDENT_HINT"), anyString(), anyLong()))
                    .thenReturn("Bước 1: Hãy xác định các hệ số a, b, c trong phương trình bậc 2.");
            when(submissionHintRepository.save(any(SubmissionHint.class))).thenAnswer(invocation -> {
                SubmissionHint sh = invocation.getArgument(0);
                sh.setId(1L);
                return sh;
            });

            StudentHintResponse response = submissionHintService.requestHint(assignmentId, request, studentEmail);

            assertThat(response).isNotNull();
            assertThat(response.getSubmissionId()).isEqualTo(submissionId);
            verify(submissionRepository, times(1)).save(any(Submission.class));
            verify(submissionHintRepository, times(1)).save(any(SubmissionHint.class));
        }

        @Test
        @DisplayName("UT-BE-03: Reached max 3 hints limit should throw HintLimitExceededException")
        void requestHint_reachedMax3Hints_throwsException() {
            StudentHintRequest request = new StudentHintRequest("Another attempt");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(3);

            assertThatThrownBy(() -> submissionHintService.requestHint(assignmentId, request, studentEmail))
                    .isInstanceOf(HintLimitExceededException.class)
                    .hasMessageContaining("Bạn đã sử dụng tối đa 3/3 lượt gợi ý");

            verify(submissionHintRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-04: AI Provider error (500/429/Exception) should not deduct quota or save hint record")
        void requestHint_aiProviderError_doesNotDeductQuota() {
            StudentHintRequest request = new StudentHintRequest("Attempt");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(1);
            when(aiPromptExecutionService.executePrompt(eq("STUDENT_HINT"), anyString(), anyLong()))
                    .thenThrow(new RuntimeException("AI Provider 429 Rate Limit"));

            assertThatThrownBy(() -> submissionHintService.requestHint(assignmentId, request, studentEmail))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("AI Provider 429 Rate Limit");

            verify(submissionHintRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-05: Submitted status submission should throw InvalidSubmissionStateException")
        void requestHint_submittedStatus_throwsException() {
            submission.setStatus(SubmissionStatus.SUBMITTED);
            StudentHintRequest request = new StudentHintRequest("Post submit request");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionHintService.requestHint(assignmentId, request, studentEmail))
                    .isInstanceOf(InvalidSubmissionStateException.class)
                    .hasMessageContaining("Bài nộp đã gửi hoặc đã được chấm điểm");

            verify(submissionHintRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-06: Past deadline assignment should throw BadRequestException")
        void requestHint_pastDeadline_throwsException() {
            assignment.setDeadline(LocalDateTime.now().minusDays(1));
            StudentHintRequest request = new StudentHintRequest("Late attempt");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));

            assertThatThrownBy(() -> submissionHintService.requestHint(assignmentId, request, studentEmail))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Đã hết hạn làm bài tập");

            verify(submissionHintRepository, never()).save(any());
        }

        @Test
        @DisplayName("UT-BE-07: Payload containing raw JSXGraph JSON should be sanitized before prompt execution")
        void requestHint_containsJsxGraphJson_sanitizesPayload() {
            String rawContent = "Em đã vẽ hình:\n\n<!-- DRAWINGS_DATA_START\n[{\"shapeCode\":\"SHAPE_1\",\"jsxGraphData\":{}}]\nDRAWINGS_DATA_END -->";
            StudentHintRequest request = new StudentHintRequest(rawContent);

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(0);
            when(aiPromptExecutionService.executePrompt(eq("STUDENT_HINT"), anyString(), anyLong()))
                    .thenReturn("Hãy kiểm tra tọa độ đỉnh của đồ thị.");
            when(submissionHintRepository.save(any(SubmissionHint.class))).thenAnswer(i -> {
                SubmissionHint sh = i.getArgument(0);
                sh.setId(1L);
                return sh;
            });

            submissionHintService.requestHint(assignmentId, request, studentEmail);

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(aiPromptExecutionService).executePrompt(eq("STUDENT_HINT"), promptCaptor.capture(), anyLong());

            String sentPrompt = promptCaptor.getValue();
            assertThat(sentPrompt).doesNotContain("DRAWINGS_DATA_START");
            assertThat(sentPrompt).doesNotContain("jsxGraphData");
        }

        @Test
        @DisplayName("UT-BE-08: Empty student content should prompt for initial approach")
        void requestHint_emptyContent_generatesInitialApproachHint() {
            StudentHintRequest request = new StudentHintRequest("   ");

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(0);
            when(aiPromptExecutionService.executePrompt(eq("STUDENT_HINT"), anyString(), anyLong()))
                    .thenReturn("Bắt đầu bằng việc xác định dạng bài toán.");
            when(submissionHintRepository.save(any(SubmissionHint.class))).thenAnswer(i -> {
                SubmissionHint sh = i.getArgument(0);
                sh.setId(1L);
                return sh;
            });

            StudentHintResponse response = submissionHintService.requestHint(assignmentId, request, studentEmail);

            assertThat(response).isNotNull();
            verify(aiPromptExecutionService).executePrompt(eq("STUDENT_HINT"), contains("[Học sinh chưa bắt đầu làm bài / Bài làm trống]"), anyLong());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when System Prompt is not configured in DB")
        void requestHint_promptNotFoundInDb_throwsResourceNotFoundException() {
            StudentHintRequest request = new StudentHintRequest("Phân tích bài toán.");
            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            when(submissionRepository.findFirstByAssignmentIdAndStudentIdWithLock(assignmentId, studentId))
                    .thenReturn(Optional.of(submission));
            when(submissionHintRepository.countBySubmissionId(submissionId)).thenReturn(0);

            doReturn(null).when(promptRenderService).renderPrompt(any());

            assertThatThrownBy(() -> submissionHintService.requestHint(assignmentId, request, studentEmail))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Chưa cấu hình System Prompt 'PROMPT_STUDENT_HINT'");
        }
    }

    @Nested
    @DisplayName("getHintHistory Tests")
    class GetHintHistoryTests {

        @Test
        @DisplayName("Should return hint history for submission owner student")
        void getHintHistory_studentOwner_success() {
            SubmissionHint hint1 = SubmissionHint.builder()
                    .submission(submission)
                    .student(student)
                    .hintNumber(1)
                    .studentSnapshotContent("x^2 - 5x + 6 = 0")
                    .aiHintContent("Tính delta")
                    .build();
            hint1.setId(1L);
            hint1.setCreatedAt(LocalDateTime.now().minusMinutes(10));

            when(userRepository.findByEmail(studentEmail)).thenReturn(Optional.of(student));
            when(submissionRepository.findByIdWithDetails(submissionId)).thenReturn(Optional.of(submission));
            when(submissionHintRepository.findBySubmissionIdOrderByHintNumberAsc(submissionId))
                    .thenReturn(Collections.singletonList(hint1));

            HintHistoryResponse history = submissionHintService.getHintHistory(submissionId, studentEmail);

            assertThat(history).isNotNull();
            assertThat(history.getTotalUsed()).isEqualTo(1);
            assertThat(history.getRemainingHints()).isEqualTo(2);
            assertThat(history.getHints()).hasSize(1);
            assertThat(history.getHints().get(0).getAiHintContent()).isEqualTo("Tính delta");
        }

        @Test
        @DisplayName("Should throw AccessDeniedException for unauthorized student viewing another student's hints")
        void getHintHistory_otherStudent_throwsAccessDeniedException() {
            User otherStudent = new User();
            otherStudent.setId(99L);
            otherStudent.setEmail("other@other.com");

            when(userRepository.findByEmail("other@other.com")).thenReturn(Optional.of(otherStudent));
            when(submissionRepository.findByIdWithDetails(submissionId)).thenReturn(Optional.of(submission));

            assertThatThrownBy(() -> submissionHintService.getHintHistory(submissionId, "other@other.com"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Bạn không có quyền xem lịch sử gợi ý này");
        }

        @Test
        @DisplayName("UT-BE-10: Should correctly compare Long IDs > 127 using Objects.equals without false 403 blocks")
        void getHintHistory_largeLongIds_success() {
            long largeStudentId = 99999L;
            long largeSubmissionId = 88888L;

            User largeStudent = new User();
            largeStudent.setId(largeStudentId);
            largeStudent.setEmail("large@codegym.com");

            Submission largeSubmission = new Submission();
            largeSubmission.setId(largeSubmissionId);
            largeSubmission.setStudent(largeStudent);
            largeSubmission.setAssignment(assignment);

            when(userRepository.findByEmail("large@codegym.com")).thenReturn(Optional.of(largeStudent));
            when(submissionRepository.findByIdWithDetails(largeSubmissionId)).thenReturn(Optional.of(largeSubmission));
            when(submissionHintRepository.findBySubmissionIdOrderByHintNumberAsc(largeSubmissionId))
                    .thenReturn(Collections.emptyList());

            HintHistoryResponse history = submissionHintService.getHintHistory(largeSubmissionId, "large@codegym.com");

            assertThat(history).isNotNull();
            assertThat(history.getSubmissionId()).isEqualTo(largeSubmissionId);
        }
    }
}
