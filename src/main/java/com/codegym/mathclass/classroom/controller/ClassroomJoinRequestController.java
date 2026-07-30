package com.codegym.mathclass.classroom.controller;

import com.codegym.mathclass.common.annotation.ApiVersion;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Classroom Join Requests", description = "APIs yêu cầu tham gia lớp học và phê duyệt yêu cầu từ giáo viên")
@RestController
@ApiVersion(1)
@RequestMapping("/classrooms")
@RequiredArgsConstructor
public class ClassroomJoinRequestController {

    private final ClassroomJoinRequestService joinRequestService;

    @Operation(summary = "Gửi yêu cầu tham gia lớp học", description = "Học sinh gửi yêu cầu xin gia nhập lớp bằng mã lớp")
    @PostMapping("/join-requests")
    @PreAuthorize("hasAuthority('classroom:join')")
    public ResponseEntity<JoinRequestResponse> requestToJoin(
            @Valid @RequestBody JoinRequestRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        JoinRequestResponse response = joinRequestService.createJoinRequest(request, currentUser.getId());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(summary = "Danh sách yêu cầu tham gia của tôi", description = "Học sinh xem lịch sử và trạng thái các yêu cầu xin gia nhập lớp")
    @GetMapping("/join-requests/me")
    @PreAuthorize("hasAuthority('classroom:join_status')")
    public ResponseEntity<List<JoinRequestResponse>> getMyJoinRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<JoinRequestResponse> responses = joinRequestService.getMyJoinRequests(currentUser.getId());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @Operation(summary = "Danh sách yêu cầu chờ duyệt của lớp", description = "Giáo viên xem danh sách các học sinh đang chờ phê duyệt vào lớp")
    @GetMapping("/{classCode}/join-requests")
    @PreAuthorize("hasAuthority('classroom:manage_requests')")
    public ResponseEntity<List<JoinRequestResponse>> getPendingRequests(
            @PathVariable String classCode,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        List<JoinRequestResponse> responses = joinRequestService.getPendingJoinRequests(classCode, currentUser.getId());
        return new ResponseEntity<>(responses, HttpStatus.OK);
    }

    @Operation(summary = "Phê duyệt hoặc Từ chối yêu cầu tham gia", description = "Giáo viên duyệt (ACCEPT) hoặc từ chối (REJECT) yêu cầu xin vào lớp")
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
