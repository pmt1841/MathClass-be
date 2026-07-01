package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.entity.AssignmentStatus;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/classrooms/{classCode}/assignments")
@RequiredArgsConstructor
public class ClassroomAssignmentController {

    private final AssignmentService assignmentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    public ResponseEntity<?> getClassroomAssignments(
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

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasAnyRole('TEACHER', 'STUDENT')")
    public ResponseEntity<?> getAssignmentDetail(
            @PathVariable String classCode,
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        String role = userDetails.getAuthorities().stream()
                .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        AssignmentResponse response = assignmentService.getAssignmentById(id, userId, role);

        if (response.getClassCode() == null || !response.getClassCode().equals(classCode)) {
            throw new com.codegym.mathclass.exception.AccessDeniedException(
                    "Bài tập này không thuộc lớp học này");
        }

        return ResponseEntity.ok(response);
    }
}
