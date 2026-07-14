package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.classroom.dto.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.ProcessJoinRequestDto;
import com.codegym.mathclass.classroom.service.ClassroomJoinRequestService;
import com.codegym.mathclass.security.services.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classrooms")
@RequiredArgsConstructor
public class ClassroomJoinRequestController {

    private final ClassroomJoinRequestService joinRequestService;

    @PostMapping("/join")
    @PreAuthorize("hasAuthority('classroom:join')")
    public ResponseEntity<JoinRequestResponse> requestToJoin(
            @Valid @RequestBody JoinRequestRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        JoinRequestResponse response = joinRequestService.createJoinRequest(request, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/my-join-requests")
    @PreAuthorize("hasAuthority('classroom:join_status')")
    public ResponseEntity<List<JoinRequestResponse>> getMyJoinRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<JoinRequestResponse> responses = joinRequestService.getMyJoinRequests(currentUser.getId());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @GetMapping("/{classCode}/join-requests")
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<List<JoinRequestResponse>> getPendingRequests(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<JoinRequestResponse> responses = joinRequestService.getPendingJoinRequests(classCode, currentUser.getId());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @PutMapping("/join-requests/{requestId}")
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<JoinRequestResponse> processRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody ProcessJoinRequestDto requestDto,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        JoinRequestResponse response = joinRequestService.processJoinRequest(requestId, requestDto, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
