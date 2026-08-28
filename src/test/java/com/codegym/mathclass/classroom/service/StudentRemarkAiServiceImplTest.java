package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.aiconfig.entity.SystemPrompt;
import com.codegym.mathclass.aiconfig.repository.SystemPromptRepository;
import com.codegym.mathclass.aiconfig.service.AiPromptExecutionService;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluateRequest;
import com.codegym.mathclass.classroom.dto.AiStudentRemarkEvaluationResponse;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentRemarkAiServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SystemPromptRepository systemPromptRepository;

    @Mock
    private AiPromptExecutionService aiPromptExecutionService;

    @InjectMocks
    private StudentRemarkAiServiceImpl studentRemarkAiService;

    private Classroom classroom;
    private User teacher;
    private User student;
    private User otherStudent;

    @BeforeEach
    void setUp() {
        teacher = User.builder()
                .fullName("Thầy Nguyễn Văn A")
                .email("teacher@mathclass.com")
                .build();
        teacher.setId(1L);

        student = User.builder()
                .fullName("Học sinh Lê Thị Bình")
                .email("student@mathclass.com")
                .build();
        student.setId(2L);

        otherStudent = User.builder()
                .fullName("Học sinh Trần Văn C")
                .email("other@mathclass.com")
                .build();
        otherStudent.setId(3L);

        classroom = Classroom.builder()
                .classCode("MATH101")
                .className("Lớp Toán 10A1")
                .teacher(teacher)
                .students(new HashSet<>(List.of(student)))
                .build();
        classroom.setId(100L);
    }

    @Test
    @DisplayName("Should evaluate student progress successfully with valid assignments and submissions")
    void evaluateStudentProgress_Success_ValidTimeframeAndData() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder()
                .days(7)
                .build();

        Assignment a1 = Assignment.builder()
                .title("Bài tập 1: Phương trình bậc 2")
                .maxScore(10.0)
                .build();
        a1.setId(101L);

        Assignment a2 = Assignment.builder()
                .title("Bài tập 2: Hệ phương trình")
                .maxScore(10.0)
                .build();
        a2.setId(102L);

        Submission s1 = Submission.builder()
                .assignment(a1)
                .student(student)
                .status(SubmissionStatus.GRADED)
                .score(9.0)
                .teacherFeedback("Làm bài tốt")
                .build();

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findPublishedAssignmentsByClassCodeAndDateRange(eq("MATH101"), any(), any()))
                .thenReturn(List.of(a1, a2));
        when(submissionRepository.findAllByAssignmentIdInAndStudentId(eq(List.of(101L, 102L)), eq(2L)))
                .thenReturn(List.of(s1));
        when(systemPromptRepository.findByCode("PROMPT_STUDENT_REMARK")).thenReturn(Optional.empty());

        String mockAiResponse = """
                {
                  "strengths": "Nắm chắc lý thuyết phương trình bậc 2, điểm số bài tập đạt 9.0.",
                  "weaknesses": "Chưa hoàn thành bài tập 2 về Hệ phương trình.",
                  "generalAssessment": "Trong khoảng thời gian từ 21/08/2026 đến 28/08/2026, học sinh đã hoàn thành 1/2 bài tập được giao. Cần chủ động làm nốt bài tập còn lại."
                }
                """;
        when(aiPromptExecutionService.executePrompt(eq("STUDENT_REMARK"), anyString(), eq(1L)))
                .thenReturn(mockAiResponse);

        AiStudentRemarkEvaluationResponse response = studentRemarkAiService.evaluateStudentProgress(
                "MATH101", 2L, 1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalAssignments()).isEqualTo(2);
        assertThat(response.getCompletedAssignments()).isEqualTo(1);
        assertThat(response.getActiveIncompleteAssignments()).isEqualTo(1);
        assertThat(response.getOverdueAssignments()).isEqualTo(0);
        assertThat(response.getAverageScore()).isEqualTo(9.0);
        assertThat(response.getStrengths()).contains("Nắm chắc lý thuyết");
        assertThat(response.getWeaknesses()).contains("Chưa hoàn thành bài tập 2");
        assertThat(response.getGeneralAssessment()).contains("hoàn thành 1/2 bài tập");
    }

    @Test
    @DisplayName("Should correctly classify overdue vs active incomplete assignments based on deadline")
    void evaluateStudentProgress_Deadlines_ClassifiesOverdueAndActive() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder().days(7).build();

        Assignment aActive = Assignment.builder()
                .title("Bài tập còn hạn")
                .deadline(java.time.LocalDateTime.now().plusDays(2))
                .build();
        aActive.setId(201L);

        Assignment aOverdue = Assignment.builder()
                .title("Bài tập quá hạn")
                .deadline(java.time.LocalDateTime.now().minusDays(1))
                .build();
        aOverdue.setId(202L);

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findPublishedAssignmentsByClassCodeAndDateRange(eq("MATH101"), any(), any()))
                .thenReturn(List.of(aActive, aOverdue));
        when(submissionRepository.findAllByAssignmentIdInAndStudentId(anyList(), eq(2L)))
                .thenReturn(Collections.emptyList());
        when(systemPromptRepository.findByCode("PROMPT_STUDENT_REMARK")).thenReturn(Optional.empty());
        when(aiPromptExecutionService.executePrompt(eq("STUDENT_REMARK"), anyString(), eq(1L)))
                .thenReturn("{\"strengths\":\"N/A\",\"weaknesses\":\"N/A\",\"generalAssessment\":\"Trong khoảng thời gian từ 21/08/2026 đến 28/08/2026, học sinh đã hoàn thành 0/2 bài tập được giao.\"}");

        AiStudentRemarkEvaluationResponse response = studentRemarkAiService.evaluateStudentProgress(
                "MATH101", 2L, 1L, request);

        assertThat(response.getTotalAssignments()).isEqualTo(2);
        assertThat(response.getCompletedAssignments()).isEqualTo(0);
        assertThat(response.getActiveIncompleteAssignments()).isEqualTo(1);
        assertThat(response.getOverdueAssignments()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should throw BadRequestException when startDate is after endDate")
    void evaluateStudentProgress_InvalidDateRange_ThrowsBadRequestException() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder()
                .startDate(LocalDate.now().plusDays(2))
                .endDate(LocalDate.now())
                .build();

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> studentRemarkAiService.evaluateStudentProgress("MATH101", 2L, 1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Ngày bắt đầu không được lớn hơn ngày kết thúc");
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when caller is not the class teacher")
    void evaluateStudentProgress_NotTeacher_ThrowsAccessDeniedException() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder().days(7).build();

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));

        assertThatThrownBy(() -> studentRemarkAiService.evaluateStudentProgress("MATH101", 2L, 99L, request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Chỉ giáo viên phụ trách lớp mới có quyền");
    }

    @Test
    @DisplayName("Should throw BadRequestException when student is not a class member")
    void evaluateStudentProgress_StudentNotMember_ThrowsBadRequestException() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder().days(7).build();

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(userRepository.findById(3L)).thenReturn(Optional.of(otherStudent));

        assertThatThrownBy(() -> studentRemarkAiService.evaluateStudentProgress("MATH101", 3L, 1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Học sinh không thuộc lớp học này");
    }

    @Test
    @DisplayName("Should fallback gracefully when AI output is not standard JSON")
    void evaluateStudentProgress_AiReturnsPlainText_GracefulFallback() {
        AiStudentRemarkEvaluateRequest request = AiStudentRemarkEvaluateRequest.builder().days(3).build();

        when(classroomRepository.findByClassCode("MATH101")).thenReturn(Optional.of(classroom));
        when(userRepository.findById(2L)).thenReturn(Optional.of(student));
        when(assignmentRepository.findPublishedAssignmentsByClassCodeAndDateRange(eq("MATH101"), any(), any()))
                .thenReturn(Collections.emptyList());
        when(systemPromptRepository.findByCode("PROMPT_STUDENT_REMARK")).thenReturn(Optional.empty());

        when(aiPromptExecutionService.executePrompt(eq("STUDENT_REMARK"), anyString(), eq(1L)))
                .thenReturn("Học sinh chăm ngoan, tiến độ học tập ổn định.");

        AiStudentRemarkEvaluationResponse response = studentRemarkAiService.evaluateStudentProgress(
                "MATH101", 2L, 1L, request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalAssignments()).isEqualTo(0);
        assertThat(response.getCompletedAssignments()).isEqualTo(0);
        assertThat(response.getGeneralAssessment()).contains("hoàn thành 0/0 bài tập");
        assertThat(response.getGeneralAssessment()).contains("Học sinh chăm ngoan");
    }
}
