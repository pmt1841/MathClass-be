package com.codegym.mathclass.bugreport.service;

import com.codegym.mathclass.bugreport.dto.request.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.response.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.request.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.dto.request.SendOtpRequest;

import java.time.LocalDateTime;

public interface BugReportService {

    void sendPublicReportOtp(SendOtpRequest request, String clientIp);

    BugReportResponse createPublicReport(CreateBugReportRequest request, String clientIp);

    BugReportResponse createAuthenticatedReport(CreateBugReportRequest request, String username, String clientIp);

    Page<BugReportResponse> getReports(BugErrorType errorType, BugReportStatus status, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    default Page<BugReportResponse> getReports(BugReportStatus status, Pageable pageable) {
        return getReports(null, status, null, null, pageable);
    }

    BugReportResponse getReportById(Long id);

    BugReportResponse updateReportStatus(Long id, UpdateBugReportStatusRequest request);
}
