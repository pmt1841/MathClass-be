package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.classroom.dto.ClassroomResponse;
import com.codegym.mathclass.classroom.dto.CreateClassroomRequest;
import com.codegym.mathclass.classroom.service.ClassroomService;
import com.codegym.mathclass.security.services.CustomUserDetails;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import com.codegym.mathclass.classroom.dto.UpdateClassroomRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Classroom Management", description = "APIs quản lý lớp học (Tạo mới, truy vấn, cập nhật, xóa lớp học)")
@RestController
@ApiVersion(1)
@RequestMapping("/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @Operation(summary = "Tạo lớp học mới", description = "Tạo một lớp học mới và gán người tạo làm Giáo viên (Teacher)")
    @PostMapping
    @PreAuthorize("hasAuthority('classroom:create')")
    public ResponseEntity<ClassroomResponse> createClassroom(
            @Valid @RequestBody CreateClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long currentUserId = userDetails.getId();
        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Lấy danh sách lớp học của người dùng", description = "Truy vấn tất cả các lớp học mà người dùng hiện tại đang tham gia hoặc giảng dạy")
    @GetMapping
    public ResponseEntity<List<ClassroomResponse>> getClassroomsList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        List<ClassroomResponse> responses = classroomService.getClassroomsListById(currentUserId);
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @Operation(summary = "Chi tiết lớp học theo mã lớp (Class Code)", description = "Lấy thông tin chi tiết của một lớp học bằng mã lớp")
    @GetMapping("/{classCode}")
    public ResponseEntity<ClassroomResponse> getClassroomByClassCode(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        ClassroomResponse response = classroomService.getClassroomByClassCode(classCode, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Cập nhật thông tin lớp học", description = "Cập nhật tên, mô tả hoặc cài đặt của lớp học")
    @PutMapping("/{classCode}")
    @PreAuthorize("hasAuthority('classroom:update')")
    public ResponseEntity<ClassroomResponse> updateClassroom(
            @PathVariable String classCode,
            @Valid @RequestBody UpdateClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        ClassroomResponse response = classroomService.updateClassroom(classCode, request, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(summary = "Xóa lớp học", description = "Xóa lớp học theo mã lớp (chỉ dành cho giáo viên chủ nhiệm hoặc quản trị viên)")
    @DeleteMapping("/{classCode}")
    @PreAuthorize("hasAuthority('classroom:delete')")
    public ResponseEntity<Void> deleteClassroom(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        classroomService.deleteClassroom(classCode, currentUserId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
