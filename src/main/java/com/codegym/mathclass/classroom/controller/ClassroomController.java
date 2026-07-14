package com.codegym.mathclass.classroom.controller;

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

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomController {

    private final ClassroomService classroomService;

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('classroom:create')")
    public ResponseEntity<ClassroomResponse> createClassroom(
            @Valid @RequestBody CreateClassroomRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        long currentUserId = userDetails.getId();
        ClassroomResponse response = classroomService.createClassroom(request, currentUserId);

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/my-classroom")
    public ResponseEntity<List<ClassroomResponse>> getClassroomsList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        List<ClassroomResponse> responses = classroomService.getClassroomsListById(currentUserId);
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @GetMapping("/{classCode}")
    public ResponseEntity<ClassroomResponse> getClassroomByClassCode(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        long currentUserId = userDetails.getId();
        ClassroomResponse response = classroomService.getClassroomByClassCode(classCode, currentUserId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

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
