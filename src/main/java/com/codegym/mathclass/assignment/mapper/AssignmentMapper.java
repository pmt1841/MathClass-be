package com.codegym.mathclass.assignment.mapper;

import com.codegym.mathclass.assignment.dto.response.AssignmentDrawingResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentImageResponse;
import com.codegym.mathclass.assignment.dto.response.AssignmentResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.dto.response.TagResponse;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AssignmentMapper {

    public AssignmentResponse toAssignmentResponse(Assignment assignment) {
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
        response.setVisibility(assignment.getVisibility());
        response.setAllowResubmit(assignment.isAllowResubmit());

        boolean open = assignment.getStatus() == AssignmentStatus.PUBLISHED
                && assignment.getDeadline() != null
                && LocalDateTime.now().isBefore(assignment.getDeadline());
        response.setOpen(open);

        response.setHasSubmissions(assignment.getStatus() == AssignmentStatus.PUBLISHED);

        if (assignment.getTeacher() != null) {
            response.setTeacherId(assignment.getTeacher().getId());
            response.setTeacherName(assignment.getTeacher().getFullName());
        }

        if (assignment.getClassroom() != null) {
            response.setClassCode(assignment.getClassroom().getClassCode());
            response.setClassName(assignment.getClassroom().getClassName());
        }

        if (assignment.getOriginalAuthor() != null) {
            response.setOriginalAuthorId(assignment.getOriginalAuthor().getId());
            response.setOriginalAuthorName(assignment.getOriginalAuthor().getFullName());
        }

        if (assignment.getAssignmentSheet() != null) {
            response.setSheetId(assignment.getAssignmentSheet().getId());
            response.setSheetTitle(assignment.getAssignmentSheet().getTitle());
        }
        
        response.setMaxScore(assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0);
        if (assignment.getAssignmentTags() != null) {
            response.setTags(assignment.getAssignmentTags().stream()
                    .map(assignmentTag -> TagResponse.fromEntity(assignmentTag.getTag()))
                    .toList());
        }

        if (assignment.getDrawings() != null && !assignment.getDrawings().isEmpty()) {
            List<AssignmentDrawingResponse> drawingResponses = assignment.getDrawings().stream().map(drawing -> {
                AssignmentDrawingResponse dr = new AssignmentDrawingResponse();
                dr.setId(drawing.getId());
                dr.setShapeCode(drawing.getShapeCode());
                dr.setJsxGraphData(drawing.getJsxGraphData());
                return dr;
            }).toList();
            response.setDrawings(drawingResponses);
        }

        if (assignment.getImages() != null && !assignment.getImages().isEmpty()) {
            List<AssignmentImageResponse> imageResponses = assignment.getImages().stream().map(image -> {
                AssignmentImageResponse img = new AssignmentImageResponse();
                img.setImageCode(image.getImageCode());
                img.setImageUrl(image.getImageUrl());
                return img;
            }).toList();
            response.setImages(imageResponses);
        }

        return response;
    }

    public AssignmentResponse toAssignmentResponseWithoutContent(Assignment assignment) {
        AssignmentResponse response = toAssignmentResponse(assignment);
        if (response != null) {
            response.setContent(null);
        }
        return response;
    }
}
