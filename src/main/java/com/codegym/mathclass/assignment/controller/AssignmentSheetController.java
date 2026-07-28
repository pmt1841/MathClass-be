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
import com.codegym.mathclass.assignment.dto.SheetCompletedStudentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Assignment Sheets", description = "APIs quản lý phiếu bài tập (Xuất bản phiếu bài tập, cập nhật, tìm kiếm và xóa phiếu bài tập)")
@RestController
@RequestMapping("/api/assignment-sheets")
@RequiredArgsConstructor
public class AssignmentSheetController {

    private final AssignmentSheetService assignmentSheetService;

    @Operation(summary = "Xuất bản phiếu bài tập", description = "Giao danh sách bài tập dưới dạng một phiếu bài tập cho các lớp học")
    @PostMapping("/publish")
    @PreAuthorize("hasAuthority('assignment:publish')")
    public ResponseEntity<?> publishAssignmentSheet(
            @Valid @RequestBody PublishAssignmentSheetRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long teacherId = userDetails.getId();
        assignmentSheetService.publishAssignmentSheet(request, teacherId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Cập nhật phiếu bài tập", description = "Chỉnh sửa tên phiếu bài tập, danh sách bài tập thuộc phiếu hoặc thời hạn nộp bài")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:update')")
    public ResponseEntity<AssignmentSheetResponse> updateAssignmentSheet(
            @PathVariable long id,
            @Valid @RequestBody UpdateAssignmentSheetRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        AssignmentSheetResponse response = assignmentSheetService.updateAssignmentSheet(id, request, userDetails.getId());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Danh sách phiếu bài tập của tôi", description = "Lấy danh sách phiếu bài tập của người dùng có hỗ trợ lọc từ khóa, mã lớp và phân trang")
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

    @Operation(summary = "Xóa phiếu bài tập", description = "Xóa một phiếu bài tập theo ID")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('assignment:delete')")
    public ResponseEntity<Void> deleteAssignmentSheet(
            @PathVariable long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        assignmentSheetService.deleteAssignmentSheet(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
    @Operation(summary = "Danh sách học sinh hoàn thành phiếu", description = "Lấy danh sách các học sinh đã nộp toàn bộ bài tập trong phiếu")
    @GetMapping("/{id}/completed-students")
    @PreAuthorize("hasAuthority('assignment:read')")
    public ResponseEntity<Page<SheetCompletedStudentResponse>> getCompletedStudentsBySheet(
            @PathVariable long id,
            @RequestParam(required = false) String classCode,
            @PageableDefault(sort = "student.id", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        Page<SheetCompletedStudentResponse> response = 
                assignmentSheetService.getCompletedStudentsBySheet(id, classCode, pageable, userDetails.getId());
        return ResponseEntity.ok(response);
    }
}
