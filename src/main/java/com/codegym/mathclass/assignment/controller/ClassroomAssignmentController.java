package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.exception.AccessDeniedException;
import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Classroom Assignments", description = "APIs truy vấn bài tập theo từng lớp học")
@RestController
@ApiVersion(1)
@RequestMapping("/classrooms/{classCode}/assignments")
@RequiredArgsConstructor
public class ClassroomAssignmentController {

    private final AssignmentService assignmentService;

    @Operation(summary = "Danh sách bài tập thuộc lớp học", description = "Lấy danh sách bài tập được giao trong một lớp học cụ thể")
    @GetMapping
    @PreAuthorize("hasAuthority('assignment:read')")
    public ResponseEntity<Page<AssignmentResponse>> getClassroomAssignments(
            @PathVariable String classCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) AssignmentStatus status,
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        Page<AssignmentResponse> responses = assignmentService.getAssignmentsByClassCode(classCode, userId, keyword,
                status, pageable);
        return ResponseEntity.ok(responses);
    }

    @Operation(summary = "Chi tiết bài tập trong lớp học", description = "Lấy chi tiết một bài tập dựa trên mã lớp và ID bài tập")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:read')")
    public ResponseEntity<AssignmentResponse> getAssignmentDetail(
            @PathVariable String classCode,
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        AssignmentResponse response = assignmentService.getAssignmentById(id, userId, role);

        if (response.getClassCode() == null || !response.getClassCode().equals(classCode)) {
            throw new AccessDeniedException(
                    "Bài tập này không thuộc lớp học này");
        }

        return ResponseEntity.ok(response);
    }
}
