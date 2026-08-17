package com.codegym.mathclass.bugreport.service;

import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.codegym.mathclass.bugreport.dto.SendOtpRequest;

public interface BugReportService {

    void sendPublicReportOtp(SendOtpRequest request, String clientIp);

    BugReportResponse createPublicReport(CreateBugReportRequest request, String clientIp);

    BugReportResponse createAuthenticatedReport(CreateBugReportRequest request, String username, String clientIp);

    Page<BugReportResponse> getReports(BugReportStatus status, Pageable pageable);

    BugReportResponse getReportById(Long id);

    BugReportResponse updateReportStatus(Long id, UpdateBugReportStatusRequest request);
}
