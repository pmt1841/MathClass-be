package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
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

import com.codegym.mathclass.classroom.dto.AddStudentRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.service.ClassroomService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.RequestParam;
import com.codegym.mathclass.security.services.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Classroom Members", description = "APIs quản lý thành viên học sinh trong lớp học (Thêm học sinh trực tiếp, xem danh sách, xóa học sinh)")
@RestController
@ApiVersion(1)
@RequestMapping("/classrooms/{classCode}")
@RequiredArgsConstructor
public class ClassroomMemberController {
    private final ClassroomService classroomService;

    @Operation(summary = "Thêm học sinh trực tiếp bằng Email", description = "Giáo viên trực tiếp thêm một học sinh vào lớp thông qua email của học sinh")
    @PostMapping("/students")
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<Void> addStudentToClass(
            @PathVariable String classCode,
            @Valid @RequestBody AddStudentRequest request,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        classroomService.addStudentToClass(classCode, request.getStudentEmail(), customUserDetails.getId());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Operation(summary = "Danh sách học sinh trong lớp", description = "Lấy danh sách học sinh tham gia lớp học có phân trang, tìm kiếm và sắp xếp")
    @GetMapping("/students")
    public ResponseEntity<Page<StudentResponse>> getStudentsByClassCode(
            @PathVariable String classCode,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "s.fullName,asc") String sort,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        if (!sortBy.startsWith("s.") && !sortBy.contains(".")) {
            sortBy = "s." + sortBy;
        }
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<StudentResponse> students = classroomService.getStudentsByClassCode(classCode, customUserDetails.getId(),
                keyword, pageable);
        return new ResponseEntity<>(students, HttpStatus.OK);
    }

    @Operation(summary = "Xóa học sinh khỏi lớp", description = "Giáo viên xóa một học sinh ra khỏi lớp học")
    @DeleteMapping("/students/{studentId}")
    @PreAuthorize("hasAuthority('classroom:remove_student')")
    public ResponseEntity<Void> removeStudentFromClass(
            @PathVariable String classCode,
            @PathVariable long studentId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        classroomService.removeStudentFromClass(classCode, studentId, customUserDetails.getId());
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
