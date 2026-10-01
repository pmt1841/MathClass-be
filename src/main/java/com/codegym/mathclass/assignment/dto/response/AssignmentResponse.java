package com.codegym.mathclass.assignment.dto.response;

import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.entity.AssignmentVisibility;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

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
    private AssignmentVisibility visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Long originalAuthorId;
    private String originalAuthorName;

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
    private Double submissionScore;

    private long teacherId;
    private String teacherName;

    // Lớp đã giao (null nếu còn là DRAFT)
    private String classCode;
    private String className;
    private List<String> publishedClassCodes;

    private List<AssignmentDrawingResponse> drawings;
    private List<AssignmentImageResponse> images;
    private List<TagResponse> tags;

    private Double maxScore;
    private boolean allowResubmit;

    private Long sheetId;
    private String sheetTitle;
    private List<SheetSiblingResponse> sheetSiblings;
}
