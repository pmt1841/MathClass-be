package com.codegym.mathclass.dashboard.service;

import com.codegym.mathclass.classroom.entity.JoinRequestStatus;
import com.codegym.mathclass.classroom.repository.ClassroomJoinRequestRepository;
import com.codegym.mathclass.classroom.repository.ClassroomRepository;
import com.codegym.mathclass.dashboard.dto.TeacherDashboardStatsDto;
import com.codegym.mathclass.submission.entity.SubmissionStatus;
import com.codegym.mathclass.submission.repository.SubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final ClassroomRepository classroomRepository;
    private final ClassroomJoinRequestRepository joinRequestRepository;
    private final SubmissionRepository submissionRepository;
    private final com.codegym.mathclass.assignment.repository.AssignmentRepository assignmentRepository;

    @Override
    public TeacherDashboardStatsDto getTeacherDashboardStats(long teacherId) {
        int teachingClasses = classroomRepository.countByTeacherId(teacherId);
        int managedStudents = classroomRepository.countDistinctStudentsByTeacherId(teacherId);
        int assignmentsToGrade = submissionRepository.countByTeacherAndStatus(teacherId, SubmissionStatus.SUBMITTED);
        int pendingJoinRequests = joinRequestRepository.countByClassroomTeacherIdAndStatus(teacherId, JoinRequestStatus.PENDING);
        int openAssignments = assignmentRepository.countByTeacherIdAndStatus(teacherId, com.codegym.mathclass.assignment.entity.AssignmentStatus.PUBLISHED);

        return TeacherDashboardStatsDto.builder()
                .teachingClasses(teachingClasses)
                .managedStudents(managedStudents)
                .assignmentsToGrade(assignmentsToGrade)
                .pendingJoinRequests(pendingJoinRequests)
                .openAssignments(openAssignments)
                .build();
    }

    @Override
    public java.util.List<com.codegym.mathclass.dashboard.dto.PendingSubmissionDto> getPendingSubmissions(long teacherId, int limit) {
        org.springframework.data.domain.Page<com.codegym.mathclass.submission.entity.Submission> submissions = submissionRepository.findPendingSubmissionsByTeacher(
                teacherId, 
                org.springframework.data.domain.PageRequest.of(0, limit)
        );

        return submissions.stream().map(s -> com.codegym.mathclass.dashboard.dto.PendingSubmissionDto.builder()
                .id(s.getId())
                .studentName(s.getStudent().getFullName())
                .assignmentTitle(s.getAssignment().getTitle())
                .className(s.getAssignment().getClassroom().getClassName())
                .classCode(s.getAssignment().getClassroom().getClassCode())
                .submittedAt(s.getSubmittedAt())
                .build()).collect(java.util.stream.Collectors.toList());
    }
    @Override
    public com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto getStudentDashboardStats(long studentId) {
        int joinedClasses = classroomRepository.countByStudentsId(studentId);
        int pendingTasks = assignmentRepository.countPendingAssignmentsForStudent(studentId);
        int completedTasks = submissionRepository.countByStudentAndStatus(studentId, SubmissionStatus.GRADED);

        return com.codegym.mathclass.dashboard.dto.StudentDashboardStatsDto.builder()
                .joinedClasses(joinedClasses)
                .pendingTasks(pendingTasks)
                .completedTasks(completedTasks)
                .build();
    }

    @Override
    public java.util.List<com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto> getStudentPendingTasks(long studentId, int limit) {
        org.springframework.data.domain.Page<com.codegym.mathclass.assignment.entity.Assignment> assignments = assignmentRepository.findPendingAssignmentsForStudent(
                studentId, 
                org.springframework.data.domain.PageRequest.of(0, limit)
        );

        return assignments.stream().map(a -> com.codegym.mathclass.dashboard.dto.StudentPendingTaskDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .classCode(a.getClassroom().getClassCode())
                .className(a.getClassroom().getClassName())
                .deadline(a.getDeadline())
                .type("Bài tập")
                .build()).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.List<com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto> getStudentGradedTasks(long studentId, int limit) {
        org.springframework.data.domain.Page<com.codegym.mathclass.submission.entity.Submission> submissions = submissionRepository.findGradedSubmissionsByStudent(
                studentId, 
                org.springframework.data.domain.PageRequest.of(0, limit)
        );

        return submissions.stream().map(s -> com.codegym.mathclass.dashboard.dto.StudentGradedTaskDto.builder()
                .id(s.getAssignment().getId())
                .title(s.getAssignment().getTitle())
                .classCode(s.getAssignment().getClassroom().getClassCode())
                .className(s.getAssignment().getClassroom().getClassName())
                .gradedAt(s.getUpdatedAt())
                .score(s.getScore() != null ? s.getScore().floatValue() : 0f)
                .maxScore(10f)
                .teacherComment(s.getTeacherFeedback())
                .build()).collect(java.util.stream.Collectors.toList());
    }
}
