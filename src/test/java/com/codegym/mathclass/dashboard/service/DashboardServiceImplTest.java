package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.dashboard.dto.*;
import com.codegym.mathclass.dashboard.service.DashboardServiceImpl;
import com.codegym.mathclass.submission.entity.Submission;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassroomJoinRequestRepository joinRequestRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private AssignmentRepository assignmentRepository;

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    private User teacher;
    private User student;
    private Classroom classroom;
    private Assignment assignment;
    private Submission submission;

    @BeforeEach
    void setUp() {
        teacher = new User();
        teacher.setId(1L);

        student = new User();
        student.setId(2L);
        student.setFullName("John Doe");

        classroom = new Classroom();
        classroom.setId(10L);
        classroom.setClassCode("MATH101");
        classroom.setClassName("Math 101");
        classroom.setTeacher(teacher);
        classroom.setStudents(Collections.singleton(student));

        assignment = new Assignment();
        assignment.setId(100L);
        assignment.setTitle("Assignment 1");
        assignment.setClassroom(classroom);

        submission = new Submission();
        submission.setId(1000L);
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setSubmittedAt(LocalDateTime.now());
        submission.setUpdatedAt(LocalDateTime.now());
        submission.setScore(9.5);
    }

    // ==========================================
    // Tests for getTeacherDashboardStats
    // ==========================================

    @Test
    @DisplayName("Should return teacher dashboard stats")
    void getTeacherDashboardStats_ValidTeacherId_ReturnsStats() {
        // Given
        when(classroomRepository.countByTeacherId(1L)).thenReturn(5);
        when(classroomRepository.countDistinctStudentsByTeacherId(1L)).thenReturn(100);
        when(submissionRepository.countByTeacherAndStatus(1L, SubmissionStatus.SUBMITTED)).thenReturn(10);
        when(joinRequestRepository.countByClassroomTeacherIdAndStatus(1L, JoinRequestStatus.PENDING)).thenReturn(2);
        when(assignmentRepository.countByTeacherIdAndStatus(1L, AssignmentStatus.PUBLISHED)).thenReturn(3);

        // When
        TeacherDashboardStatsDto stats = dashboardService.getTeacherDashboardStats(1L);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getTeachingClasses()).isEqualTo(5);
        assertThat(stats.getManagedStudents()).isEqualTo(100);
        assertThat(stats.getAssignmentsToGrade()).isEqualTo(10);
        assertThat(stats.getPendingJoinRequests()).isEqualTo(2);
        assertThat(stats.getOpenAssignments()).isEqualTo(3);
    }

    // ==========================================
    // Tests for getPendingSubmissions
    // ==========================================

    @Test
    @DisplayName("Should return pending submissions")
    void getPendingSubmissions_ValidData_ReturnsList() {
        // Given
        Page<Submission> page = new PageImpl<>(Collections.singletonList(submission));
        when(submissionRepository.findPendingSubmissionsByTeacher(eq(1L), any(Pageable.class))).thenReturn(page);

        // When
        List<PendingSubmissionDto> list = dashboardService.getPendingSubmissions(1L, 10);

        // Then
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(1000L);
        assertThat(list.get(0).getStudentName()).isEqualTo("John Doe");
        assertThat(list.get(0).getAssignmentTitle()).isEqualTo("Assignment 1");
        assertThat(list.get(0).getClassName()).isEqualTo("Math 101");
        assertThat(list.get(0).getClassCode()).isEqualTo("MATH101");
    }

    // ==========================================
    // Tests for getStudentDashboardStats
    // ==========================================

    @Test
    @DisplayName("Should return student dashboard stats")
    void getStudentDashboardStats_ValidStudentId_ReturnsStats() {
        // Given
        when(classroomRepository.countByStudentsId(2L)).thenReturn(3);
        when(assignmentRepository.countPendingAssignmentsForStudent(2L)).thenReturn(5);
        when(submissionRepository.countByStudentAndStatus(2L, SubmissionStatus.GRADED)).thenReturn(15);

        // When
        StudentDashboardStatsDto stats = dashboardService.getStudentDashboardStats(2L);

        // Then
        assertThat(stats).isNotNull();
        assertThat(stats.getJoinedClasses()).isEqualTo(3);
        assertThat(stats.getPendingTasks()).isEqualTo(5);
        assertThat(stats.getCompletedTasks()).isEqualTo(15);
    }

    // ==========================================
    // Tests for getStudentPendingTasks
    // ==========================================

    @Test
    @DisplayName("Should return student pending tasks")
    void getStudentPendingTasks_ValidData_ReturnsList() {
        // Given
        Page<Assignment> page = new PageImpl<>(Collections.singletonList(assignment));
        when(assignmentRepository.findPendingAssignmentsForStudent(eq(2L), any(Pageable.class))).thenReturn(page);

        // When
        List<StudentPendingTaskDto> list = dashboardService.getStudentPendingTasks(2L, 10);

        // Then
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(100L);
        assertThat(list.get(0).getTitle()).isEqualTo("Assignment 1");
        assertThat(list.get(0).getClassCode()).isEqualTo("MATH101");
        assertThat(list.get(0).getClassName()).isEqualTo("Math 101");
        assertThat(list.get(0).getType()).isEqualTo("Bài tập");
    }

    // ==========================================
    // Tests for getStudentGradedTasks
    // ==========================================

    @Test
    @DisplayName("Should return student graded tasks")
    void getStudentGradedTasks_ValidData_ReturnsList() {
        // Given
        Page<Submission> page = new PageImpl<>(Collections.singletonList(submission));
        when(submissionRepository.findGradedSubmissionsByStudent(eq(2L), any(Pageable.class))).thenReturn(page);

        // When
        List<StudentGradedTaskDto> list = dashboardService.getStudentGradedTasks(2L, 10);

        // Then
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getId()).isEqualTo(100L); // it maps s.getAssignment().getId()
        assertThat(list.get(0).getTitle()).isEqualTo("Assignment 1");
        assertThat(list.get(0).getClassCode()).isEqualTo("MATH101");
        assertThat(list.get(0).getClassName()).isEqualTo("Math 101");
        assertThat(list.get(0).getScore()).isEqualTo(9.5f);
    }

    // ==========================================
    // Tests for getAtRiskStudents
    // ==========================================

    @Test
    @DisplayName("Should return at-risk students")
    void getAtRiskStudents_ValidData_ReturnsList() {
        // Given
        Object[] lowScoreResult = new Object[]{student, 4.5};
        when(submissionRepository.findStudentsWithLowAverageScore(1L)).thenReturn(Collections.singletonList(lowScoreResult));
        when(classroomRepository.findByStudentsId(2L)).thenReturn(Collections.singletonList(classroom));

        Assignment assignment1 = new Assignment();
        assignment1.setId(101L);
        assignment1.setClassroom(classroom);

        Assignment assignment2 = new Assignment();
        assignment2.setId(102L);
        assignment2.setClassroom(classroom);
        
        Assignment assignment3 = new Assignment();
        assignment3.setId(103L);
        assignment3.setClassroom(classroom);

        when(assignmentRepository.findAssignmentsPastDeadline(eq(1L), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(assignment1, assignment2, assignment3));

        when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(eq(101L), eq(2L), eq(SubmissionStatus.DRAFT))).thenReturn(false);
        when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(eq(102L), eq(2L), eq(SubmissionStatus.DRAFT))).thenReturn(false);
        when(submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(eq(103L), eq(2L), eq(SubmissionStatus.DRAFT))).thenReturn(false);

        // When
        List<AtRiskStudentDto> list = dashboardService.getAtRiskStudents(1L);

        // Then
        // Expect 2 items: 1 for low score, 1 for missing assignments (>2)
        assertThat(list).hasSize(2);
        
        AtRiskStudentDto lowScoreDto = list.get(0);
        assertThat(lowScoreDto.getId()).isEqualTo(2L);
        assertThat(lowScoreDto.getIssueType()).isEqualTo("low_score");
        assertThat(lowScoreDto.getDetail()).isEqualTo("Điểm TB: 4.5");

        AtRiskStudentDto missingDto = list.get(1);
        assertThat(missingDto.getId()).isEqualTo(2L);
        assertThat(missingDto.getIssueType()).isEqualTo("missing_assignments");
        assertThat(missingDto.getDetail()).isEqualTo("Thiếu 3 bài tập");
    }
}
