package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.CreateStudentRemarkRequest;
import com.codegym.mathclass.classroom.dto.StudentRemarkResponse;
import com.codegym.mathclass.classroom.service.StudentRemarkService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Student Remarks", description = "APIs quản lý lịch sử nhận xét học sinh (Điểm mạnh, điểm yếu)")
@RestController
@ApiVersion(1)
@RequestMapping("/classrooms/{classCode}/students/{studentId}/remarks")
@RequiredArgsConstructor
public class StudentRemarkController {

    private final StudentRemarkService studentRemarkService;

    @Operation(summary = "Lấy lịch sử nhận xét của học sinh", description = "Truy vấn danh sách các nhận xét (điểm mạnh, điểm yếu) của học sinh trong lớp theo thời gian")
    @GetMapping
    public ResponseEntity<List<StudentRemarkResponse>> getStudentRemarks(
            @PathVariable String classCode,
            @PathVariable Long studentId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        List<StudentRemarkResponse> remarks = studentRemarkService.getStudentRemarks(
                classCode, studentId, customUserDetails.getId());
        return ResponseEntity.ok(remarks);
    }

    @Operation(summary = "Tạo nhận xét học sinh mới", description = "Giáo viên viết nhận xét điểm mạnh và điểm yếu cho học sinh trong lớp")
    @PostMapping
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<StudentRemarkResponse> createStudentRemark(
            @PathVariable String classCode,
            @PathVariable Long studentId,
            @Valid @RequestBody CreateStudentRemarkRequest request,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        StudentRemarkResponse response = studentRemarkService.createStudentRemark(
                classCode, studentId, customUserDetails.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Xóa nhận xét", description = "Giáo viên xóa một nhận xét theo ID")
    @DeleteMapping("/{remarkId}")
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<Void> deleteStudentRemark(
            @PathVariable String classCode,
            @PathVariable Long studentId,
            @PathVariable Long remarkId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        studentRemarkService.deleteStudentRemark(classCode, studentId, remarkId, customUserDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
