package com.codegym.mathclass.assignment.dto;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime deadline;
    private AssignmentStatus status;

    // isOpen: tính tự động theo deadline, không lưu vào DB
    // true → PUBLISHED và còn trong hạn nộp
    // false → DRAFT hoặc đã quá hạn
    private boolean isOpen;

    private Long teacherId;
    private String teacherName;

    // Lớp đã giao (null nếu còn là DRAFT)
    private String classCode;
    private String className;

    public static AssignmentResponse fromEntity(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        AssignmentResponse response = new AssignmentResponse();
        response.setId(assignment.getId());
        response.setTitle(assignment.getTitle());
        response.setDescription(assignment.getDescription());
        response.setDeadline(assignment.getDeadline());
        response.setStatus(assignment.getStatus());

        // Tính isOpen tự động: chỉ mở khi PUBLISHED và chưa quá deadline
        boolean open = assignment.getStatus() == AssignmentStatus.PUBLISHED
                && assignment.getDeadline() != null
                && LocalDateTime.now().isBefore(assignment.getDeadline());
        response.setOpen(open);

        if (assignment.getTeacher() != null) {
            response.setTeacherId(assignment.getTeacher().getId());
            response.setTeacherName(assignment.getTeacher().getFullName());
        }

        if (assignment.getClassroom() != null) {
            response.setClassCode(assignment.getClassroom().getClassCode());
            response.setClassName(assignment.getClassroom().getClassName());
        }

        return response;
    }
}
