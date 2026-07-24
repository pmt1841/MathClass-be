package com.codegym.mathclass.assignment.controller;

import com.codegym.mathclass.assignment.dto.AssignmentResponse;
import com.codegym.mathclass.assignment.dto.AssignmentSheetResponse;
import com.codegym.mathclass.assignment.service.AssignmentService;
import com.codegym.mathclass.assignment.service.AssignmentSheetService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Assignment Library", description = "APIs Thư viện bài tập dùng chung (Tìm kiếm và Clone bài tập/phiếu bài tập công khai)")
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class AssignmentLibraryController {

    private final AssignmentService assignmentService;
    private final AssignmentSheetService assignmentSheetService;

    @Operation(summary = "Tìm kiếm bài tập đơn lẻ công khai trong Thư viện")
    @GetMapping("/assignments")
    @PreAuthorize("hasAuthority('library:read')")
    public ResponseEntity<Page<AssignmentResponse>> getPublicAssignments(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AssignmentResponse> assignments = assignmentService.getPublicAssignments(keyword, pageable);
        return ResponseEntity.ok(assignments);
    }

    @Operation(summary = "Clone bài tập đơn lẻ từ Thư viện về kho cá nhân")
    @PostMapping("/assignments/{id}/clone")
    @PreAuthorize("hasAuthority('library:clone')")
    public ResponseEntity<AssignmentResponse> cloneAssignmentFromLibrary(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AssignmentResponse response = assignmentService.cloneAssignmentFromLibrary(id, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Tìm kiếm phiếu bài tập công khai trong Thư viện")
    @GetMapping("/assignment-sheets")
    @PreAuthorize("hasAuthority('library:read')")
    public ResponseEntity<Page<AssignmentSheetResponse>> getPublicAssignmentSheets(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AssignmentSheetResponse> sheets = assignmentSheetService.getPublicAssignmentSheets(keyword, pageable);
        return ResponseEntity.ok(sheets);
    }

    @Operation(summary = "Clone phiếu bài tập từ Thư viện về kho cá nhân")
    @PostMapping("/assignment-sheets/{id}/clone")
    @PreAuthorize("hasAuthority('library:clone')")
    public ResponseEntity<AssignmentSheetResponse> cloneAssignmentSheetFromLibrary(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AssignmentSheetResponse response = assignmentSheetService.cloneAssignmentSheetFromLibrary(id, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
