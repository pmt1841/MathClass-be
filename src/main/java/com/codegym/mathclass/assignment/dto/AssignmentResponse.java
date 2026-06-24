package com.codegym.mathclass.assignment.dto;

import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResponse {

    private long id;
    private String title;
    private String description;
    private String content;
    private LocalDateTime deadline;
    private AssignmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // isOpen: tính tự động theo deadline, không lưu vào DB
    // true → PUBLISHED và còn trong hạn nộp
    // false → DRAFT hoặc đã quá hạn
    private boolean isOpen;

    // Giả lập (mock) kiểm tra xem bài tập đã có học sinh nộp chưa
    private boolean hasSubmissions;

    // Trạng thái nộp bài của học sinh hiện tại (DRAFT, SUBMITTED, GRADED hoặc null)
    private String submissionStatus;
    private LocalDateTime submissionCreatedAt;
    private LocalDateTime submissionUpdatedAt;

    private long teacherId;
    private String teacherName;

    // Lớp đã giao (null nếu còn là DRAFT)
    private String classCode;
    private String className;

    private List<AssignmentDrawingResponse> drawings;
    private List<AssignmentImageDto> images;

    public static AssignmentResponse fromEntity(Assignment assignment) {
        if (assignment == null) {
            return null;
        }

        AssignmentResponse response = new AssignmentResponse();
        response.setId(assignment.getId());
        response.setTitle(assignment.getTitle());
        response.setDescription(assignment.getDescription());
        response.setContent(assignment.getContent());
        response.setDeadline(assignment.getDeadline());
        response.setStatus(assignment.getStatus());
        response.setCreatedAt(assignment.getCreatedAt());
        response.setUpdatedAt(assignment.getUpdatedAt());

        // Tính isOpen tự động: chỉ mở khi PUBLISHED và chưa quá deadline
        boolean open = assignment.getStatus() == AssignmentStatus.PUBLISHED
                && assignment.getDeadline() != null
                && LocalDateTime.now().isBefore(assignment.getDeadline());
        response.setOpen(open);

        // Giả lập: Nếu là PUBLISHED thì mặc định là đã có người nộp (true)
        response.setHasSubmissions(assignment.getStatus() == AssignmentStatus.PUBLISHED);

        if (assignment.getTeacher() != null) {
            response.setTeacherId(assignment.getTeacher().getId());
            response.setTeacherName(assignment.getTeacher().getFullName());
        }

        if (assignment.getClassroom() != null) {
            response.setClassCode(assignment.getClassroom().getClassCode());
            response.setClassName(assignment.getClassroom().getClassName());
        }

        if (assignment.getDrawings() != null && !assignment.getDrawings().isEmpty()) {
            List<AssignmentDrawingResponse> drawingResponses = assignment.getDrawings().stream().map(drawing -> {
                AssignmentDrawingResponse dr = new AssignmentDrawingResponse();
                dr.setId(drawing.getId());
                dr.setShapeCode(drawing.getShapeCode());
                dr.setJsxGraphData(drawing.getJsxGraphData());
                return dr;
            }).collect(Collectors.toList());
            response.setDrawings(drawingResponses);
        }

        return response;
    }

    public static AssignmentResponse fromEntityWithoutContent(Assignment assignment) {
        AssignmentResponse response = fromEntity(assignment);
        if (response != null) {
            response.setContent(null);
        }
        return response;
    }
}
