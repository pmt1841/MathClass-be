package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.dto.PublishAssignmentSheetRequest;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.codegym.mathclass.assignment.dto.UpdateAssignmentSheetRequest;

@RestController
@RequestMapping("/api/assignment-sheets")
@RequiredArgsConstructor
public class AssignmentSheetController {

    private final AssignmentSheetService assignmentSheetService;

    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('assignment:publish')")
    public ResponseEntity<?> publishAssignmentSheet(
            @Valid @RequestBody PublishAssignmentSheetRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long teacherId = userDetails.getId();
        assignmentSheetService.publishAssignmentSheet(request, teacherId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:update')")
    public ResponseEntity<AssignmentSheetResponse> updateAssignmentSheet(
            @PathVariable long id,
            @Valid @RequestBody UpdateAssignmentSheetRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AssignmentSheetResponse response = assignmentSheetService.updateAssignmentSheet(id, request, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<AssignmentSheetResponse>> getAssignmentSheetsForCurrentUser(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String classCode,
            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long userId = userDetails.getId();
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.replace("ROLE_", ""))
                .findFirst()
                .orElse("");

        Page<AssignmentSheetResponse> sheets = assignmentSheetService.getAssignmentSheetsForCurrentUser(
                userId, role, keyword, classCode, pageable);

        return ResponseEntity.ok(sheets);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:delete')")
    public ResponseEntity<Void> deleteAssignmentSheet(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        assignmentSheetService.deleteAssignmentSheet(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
