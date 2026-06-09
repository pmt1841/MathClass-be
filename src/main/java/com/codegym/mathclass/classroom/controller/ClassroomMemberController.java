package com.codegym.mathclass.classroom.controller;

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

@RestController
@RequestMapping("/api/classrooms/{classCode}")
@RequiredArgsConstructor
public class ClassroomMemberController {
    private final ClassroomService classroomService;

    @PostMapping("/students/add")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> addStudentToClass(
            @PathVariable String classCode,
            @Valid @RequestBody AddStudentRequest request,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        classroomService.addStudentToClass(classCode, request.getStudentEmail(), customUserDetails.getId());
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/students")
    public ResponseEntity<Page<StudentResponse>> getStudentsByClassCode(
            @PathVariable String classCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "s.fullName,asc") String sort,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        String[] sortParams = sort.split(",");
        String sortBy = sortParams[0];
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("desc") ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        Page<StudentResponse> students = classroomService.getStudentsByClassCode(classCode, customUserDetails.getId(),
                pageable);
        return new ResponseEntity<>(students, HttpStatus.OK);
    }

    @DeleteMapping("/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> removeStudentFromClass(
            @PathVariable String classCode,
            @PathVariable long studentId,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        classroomService.removeStudentFromClass(classCode, studentId, customUserDetails.getId());
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
