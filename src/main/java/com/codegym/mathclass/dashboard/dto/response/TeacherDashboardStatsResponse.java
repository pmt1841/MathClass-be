package com.codegym.mathclass.dashboard.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherDashboardStatsResponse {
    private int teachingClasses;
    private int managedStudents;
    private int assignmentsToGrade;
    private int pendingJoinRequests;
    private int openAssignments;
    private int originalAssignmentSheets;
}
