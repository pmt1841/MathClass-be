package com.codegym.mathclass.assignment.mapper;

import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.entity.AssignmentSheet;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Mapper chuyên trách chuyển đổi đối tượng {@link AssignmentSheet} sang {@link AssignmentSheetResponse}.
 */
@Component
@RequiredArgsConstructor
public class AssignmentSheetMapper {

    private final AssignmentMapper assignmentMapper;

    /**
     * Chuyển đổi entity {@link AssignmentSheet} sang {@link AssignmentSheetResponse} đầy đủ thông tin kèm danh sách bài tập con (without content).
     *
     * @param sheet Phiếu bài tập entity
     * @return {@link AssignmentSheetResponse}, hoặc null nếu sheet null
     */
    public AssignmentSheetResponse toResponse(AssignmentSheet sheet) {
        if (sheet == null) {
            return null;
        }

        AssignmentSheetResponse res = mapBaseProperties(sheet);

        if (sheet.getItems() != null) {
            List<AssignmentResponse> items = sheet.getItems().stream()
                    .filter(asgn -> asgn != null && asgn.getStatus() != AssignmentStatus.DELETED)
                    .map(asgn -> {
                        AssignmentResponse ar = assignmentMapper.toAssignmentResponseWithoutContent(asgn);
                        if (ar != null && asgn.getMaxScore() != null) {
                            ar.setMaxScore(asgn.getMaxScore());
                        }
                        return ar;
                    })
                    .filter(Objects::nonNull)
                    .toList();
            res.setItems(items);
        }

        return res;
    }

    /**
     * Chuyển đổi entity {@link AssignmentSheet} sang {@link AssignmentSheetResponse} không chứa danh sách bài tập con.
     *
     * <p>Tối ưu hiệu năng và tránh kích hoạt lazy-loading khi hiển thị danh sách tóm tắt.
     *
     * @param sheet Phiếu bài tập entity
     * @return {@link AssignmentSheetResponse} không có items
     */
    public AssignmentSheetResponse toResponseWithoutContent(AssignmentSheet sheet) {
        if (sheet == null) {
            return null;
        }
        return mapBaseProperties(sheet);
    }

    private AssignmentSheetResponse mapBaseProperties(AssignmentSheet sheet) {
        AssignmentSheetResponse res = new AssignmentSheetResponse();
        res.setId(sheet.getId());
        res.setTitle(sheet.getTitle());
        res.setDescription(sheet.getDescription());
        res.setDeadline(sheet.getDeadline());
        res.setVisibility(sheet.getVisibility());
        res.setCreatedAt(sheet.getCreatedAt());
        res.setUpdatedAt(sheet.getUpdatedAt());

        if (sheet.getTeacher() != null) {
            res.setTeacherId(sheet.getTeacher().getId());
            res.setTeacherName(sheet.getTeacher().getFullName());
        }

        if (sheet.getOriginalAuthor() != null) {
            res.setOriginalAuthorId(sheet.getOriginalAuthor().getId());
            res.setOriginalAuthorName(sheet.getOriginalAuthor().getFullName());
        }

        if (sheet.getClassroom() != null) {
            res.setClassCode(sheet.getClassroom().getClassCode());
            res.setClassName(sheet.getClassroom().getClassName());
        }

        if (sheet.getMasterSheet() != null) {
            res.setMasterSheetId(sheet.getMasterSheet().getId());
        }

        return res;
    }
}
