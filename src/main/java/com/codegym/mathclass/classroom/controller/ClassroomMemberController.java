package com.codegym.mathclass.classroom.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codegym.mathclass.classroom.dto.AddStudentRequest;
import com.codegym.mathclass.classroom.dto.StudentResponse;
import com.codegym.mathclass.classroom.service.ClassroomService;
import java.util.List;
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
    public ResponseEntity<List<StudentResponse>> getStudentsByClassCode(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails customUserDetails) {

        List<StudentResponse> students = classroomService.getStudentsByClassCode(classCode, customUserDetails.getId());
        return new ResponseEntity<>(students, HttpStatus.OK);
    }
}
