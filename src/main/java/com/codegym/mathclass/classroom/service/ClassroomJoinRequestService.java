package com.codegym.mathclass.classroom.service;

import com.codegym.mathclass.classroom.dto.JoinRequestRequest;
import com.codegym.mathclass.classroom.dto.JoinRequestResponse;
import com.codegym.mathclass.classroom.dto.ProcessJoinRequestDto;

import java.util.List;

public interface ClassroomJoinRequestService {
    
    JoinRequestResponse createJoinRequest(JoinRequestRequest request, long studentId);

    List<JoinRequestResponse> getPendingJoinRequests(String classCode, long teacherId);

    JoinRequestResponse processJoinRequest(Long requestId, ProcessJoinRequestDto requestDto, long teacherId);

    List<JoinRequestResponse> getMyJoinRequests(long studentId);
}
