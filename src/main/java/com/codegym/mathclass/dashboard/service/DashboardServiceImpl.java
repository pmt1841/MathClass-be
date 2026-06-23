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

    @Override
    public TeacherDashboardStatsDto getTeacherDashboardStats(long teacherId) {
        int teachingClasses = classroomRepository.countByTeacherId(teacherId);
        int managedStudents = classroomRepository.countDistinctStudentsByTeacherId(teacherId);
        int assignmentsToGrade = submissionRepository.countByTeacherAndStatus(teacherId, SubmissionStatus.SUBMITTED);
        int pendingJoinRequests = joinRequestRepository.countByClassroomTeacherIdAndStatus(teacherId, JoinRequestStatus.PENDING);

        return TeacherDashboardStatsDto.builder()
                .teachingClasses(teachingClasses)
                .managedStudents(managedStudents)
                .assignmentsToGrade(assignmentsToGrade)
                .pendingJoinRequests(pendingJoinRequests)
                .build();
    }
}
