package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.request.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.response.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.request.ProcessJoinRequest;

import java.util.List;

public interface ClassroomJoinRequestService {
    
    JoinRequestResponse createJoinRequest(JoinRequestRequest request, long studentId);

    List<JoinRequestResponse> getPendingJoinRequests(String classCode, long teacherId);

    JoinRequestResponse processJoinRequest(Long requestId, ProcessJoinRequest requestDto, long teacherId);

    List<JoinRequestResponse> getMyJoinRequests(long studentId);
}
