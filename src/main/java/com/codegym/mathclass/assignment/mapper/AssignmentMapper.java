package com.codegym.mathclass.assignment.mapper;

import com.codegym.mathclass.assignment.dto.AssignmentDrawingResponse;
import com.codegym.mathclass.assignment.dto.AssignmentImageDto;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.entity.Assignment;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

        if (assignment.getMasterSheet() != null) {
            response.setSheetId(assignment.getMasterSheet().getId());
            response.setSheetTitle(assignment.getMasterSheet().getTitle());
        }
        
        response.setMaxScore(assignment.getMaxScore() != null ? assignment.getMaxScore() : 10.0);

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

        if (assignment.getImages() != null && !assignment.getImages().isEmpty()) {
            List<AssignmentImageDto> imageResponses = assignment.getImages().stream().map(image -> {
                AssignmentImageDto img = new AssignmentImageDto();
                img.setImageCode(image.getImageCode());
                img.setImageUrl(image.getImageUrl());
                return img;
            }).collect(Collectors.toList());
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
