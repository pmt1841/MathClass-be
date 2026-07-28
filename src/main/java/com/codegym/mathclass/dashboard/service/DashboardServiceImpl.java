package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.dashboard.dto.TeacherDashboardStatsDto;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.repository.AssignmentRepository;
import com.codegym.mathclass.assignment.repository.AssignmentSheetRepository;
import com.codegym.mathclass.dashboard.dto.PendingSubmissionDto;
import com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto;
import com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto;
import com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto;
import com.codegym.mathclass.submission.entity.Submission;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.codegym.mathclass.classroom.entity.Classroom;
import com.codegym.mathclass.dashboard.dto.AtRiskStudentDto;
import com.codegym.mathclass.user.entity.User;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomJoinRequestRepository joinRequestRepository;
    private final SubmissionRepository submissionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSheetRepository assignmentSheetRepository;

    @Override
    public TeacherDashboardStatsDto getTeacherDashboardStats(long teacherId) {
        int teachingClasses = classroomRepository.countByTeacherId(teacherId);
        int managedStudents = classroomRepository.countDistinctStudentsByTeacherId(teacherId);
        int assignmentsToGrade = submissionRepository.countByTeacherAndStatus(teacherId, SubmissionStatus.SUBMITTED);
        int pendingJoinRequests = joinRequestRepository.countByClassroomTeacherIdAndStatus(teacherId, JoinRequestStatus.PENDING);
        int openAssignments = assignmentRepository.countByTeacherIdAndStatus(teacherId, AssignmentStatus.ARCHIVED);
        int originalAssignmentSheets = assignmentSheetRepository.countByTeacherIdAndClassroomIsNull(teacherId);

        return TeacherDashboardStatsDto.builder()
                .teachingClasses(teachingClasses)
                .managedStudents(managedStudents)
                .assignmentsToGrade(assignmentsToGrade)
                .pendingJoinRequests(pendingJoinRequests)
                .openAssignments(openAssignments)
                .originalAssignmentSheets(originalAssignmentSheets)
                .build();
    }

    @Override
    public List<PendingSubmissionDto> getPendingSubmissions(long teacherId, int limit) {
        Page<Submission> submissions = submissionRepository.findPendingSubmissionsByTeacher(
                teacherId, 
                PageRequest.of(0, limit)
        );

        return submissions.stream().map(s -> PendingSubmissionDto.builder()
                .id(s.getId())
                .assignmentId(s.getAssignment().getId())
                .studentName(s.getStudent().getFullName())
                .assignmentTitle(s.getAssignment().getTitle())
                .className(s.getAssignment().getClassroom().getClassName())
                .classCode(s.getAssignment().getClassroom().getClassCode())
                .submittedAt(s.getSubmittedAt())
                .build()).collect(Collectors.toList());
    }
    @Override
    public StudentDashboardStatsDto getStudentDashboardStats(long studentId) {
        int joinedClasses = classroomRepository.countByStudentsId(studentId);
        int pendingTasks = assignmentRepository.countPendingAssignmentsForStudent(studentId);
        int completedTasks = submissionRepository.countByStudentAndStatus(studentId, SubmissionStatus.GRADED);

        return StudentDashboardStatsDto.builder()
                .joinedClasses(joinedClasses)
                .pendingTasks(pendingTasks)
                .completedTasks(completedTasks)
                .build();
    }

    @Override
    public List<StudentPendingTaskDto> getStudentPendingTasks(long studentId, int limit) {
        Page<Assignment> assignments = assignmentRepository.findPendingAssignmentsForStudent(
                studentId, 
                PageRequest.of(0, limit)
        );

        return assignments.stream().map(a -> StudentPendingTaskDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .classCode(a.getClassroom().getClassCode())
                .className(a.getClassroom().getClassName())
                .deadline(a.getDeadline())
                .type("Bài tập")
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<StudentGradedTaskDto> getStudentGradedTasks(long studentId, int limit) {
        Page<Submission> submissions = submissionRepository.findGradedSubmissionsByStudent(
                studentId, 
                PageRequest.of(0, limit)
        );

        return submissions.stream().map(s -> StudentGradedTaskDto.builder()
                .id(s.getAssignment().getId())
                .title(s.getAssignment().getTitle())
                .classCode(s.getAssignment().getClassroom().getClassCode())
                .className(s.getAssignment().getClassroom().getClassName())
                .gradedAt(s.getUpdatedAt())
                .score(s.getScore() != null ? s.getScore().floatValue() : 0f)
                .maxScore(10f)
                .teacherComment(s.getTeacherFeedback())
                .build()).collect(Collectors.toList());
    }

    @Override
    public List<AtRiskStudentDto> getAtRiskStudents(long teacherId) {
        List<AtRiskStudentDto> atRiskStudents = new ArrayList<>();
        
        // 1. Học sinh điểm trung bình < 5.0
        List<Object[]> lowAvgScores = submissionRepository.findStudentsWithLowAverageScore(teacherId);
        for (Object[] result : lowAvgScores) {
            User student = (User) result[0];
            Double avgScore = (Double) result[1];
            
            String className = getStudentClassName(teacherId, student);

            atRiskStudents.add(AtRiskStudentDto.builder()
                .id(student.getId())
                .name(student.getFullName())
                .className(className)
                .issueType("low_score")
                .detail(String.format("Điểm TB: %.1f", avgScore))
                .avatar(student.getFullName().substring(0, 1).toUpperCase())
                .build());
        }

        // 2. Học sinh thiếu > 2 bài nộp
        List<Assignment> pastAssignments = assignmentRepository.findAssignmentsPastDeadline(teacherId, LocalDateTime.now());
        Map<User, Integer> missingCountMap = new HashMap<>();
        
        for (Assignment a : pastAssignments) {
            for (User student : a.getClassroom().getStudents()) {
                boolean submitted = submissionRepository.existsByAssignmentIdAndStudentIdAndStatusNot(a.getId(), student.getId(), SubmissionStatus.DRAFT);
                if (!submitted) {
                    missingCountMap.put(student, missingCountMap.getOrDefault(student, 0) + 1);
                }
            }
        }

        for (Map.Entry<User, Integer> entry : missingCountMap.entrySet()) {
            if (entry.getValue() > 2) {
                User student = entry.getKey();
                String className = getStudentClassName(teacherId, student);

                atRiskStudents.add(AtRiskStudentDto.builder()
                    .id(student.getId())
                    .name(student.getFullName())
                    .className(className)
                    .issueType("missing_assignments")
                    .detail("Thiếu " + entry.getValue() + " bài tập")
                    .avatar(student.getFullName().substring(0, 1).toUpperCase())
                    .build());
            }
        }

        return atRiskStudents;
    }

    private String getStudentClassName(long teacherId, User student) {
        List<Classroom> classes = classroomRepository.findByStudentsId(student.getId());
        for (Classroom c : classes) {
            if (c.getTeacher().getId() == teacherId) {
                return c.getClassName();
            }
        }
        return "N/A";
    }
}
