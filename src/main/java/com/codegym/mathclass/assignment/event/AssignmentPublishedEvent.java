package com.codegym.mathclass.assignment.event;

import java.util.List;

/**
 * Event đại diện cho hành động xuất bản bài tập đơn lẻ thành công tới một hoặc nhiều lớp học.
 * Chứa thông tin đã được đóng gói sẵn để xử lý bất đồng bộ sau transaction commit mà không
 * phụ thuộc vào Hibernate Session.
 */
public record AssignmentPublishedEvent(
        Long originalAssignmentId,
        Long teacherId,
        List<PublishedTarget> targets
) {
    public record PublishedTarget(
            Long assignmentId,
            String assignmentTitle,
            String classCode,
            String className,
            List<StudentRecipient> recipients
    ) {}

    public record StudentRecipient(
            Long studentId,
            String email,
            String fullName
    ) {}
}
