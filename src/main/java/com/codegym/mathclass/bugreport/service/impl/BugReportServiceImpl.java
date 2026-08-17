package com.codegym.mathclass.bugreport.service.impl;

import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugErrorType;
import com.codegym.mathclass.bugreport.entity.BugReport;
import com.codegym.mathclass.bugreport.entity.BugReportImage;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.repository.BugReportRepository;
import com.codegym.mathclass.bugreport.service.BugReportService;
import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.exception.ResourceNotFoundException;
import com.codegym.mathclass.notification.service.NotificationService;
import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.utils.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BugReportServiceImpl implements BugReportService {

    private final BugReportRepository bugReportRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public BugReportResponse createPublicReport(CreateBugReportRequest request) {
        if (request.getReporterEmail() == null || request.getReporterEmail().trim().isEmpty()) {
            throw new BadRequestException("Email không được để trống đối với báo cáo công khai");
        }

        String safeName = request.getReporterName() != null ? request.getReporterName().trim() : null;
        String safeDescription = request.getDescription() != null ? request.getDescription().trim() : null;

        BugReport bugReport = BugReport.builder()
                .reporterEmail(request.getReporterEmail().trim())
                .reporterName(safeName)
                .errorType(request.getErrorType())
                .description(safeDescription)
                .status(BugReportStatus.PENDING)
                .build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<BugReportImage> images = request.getImageUrls().stream()
                    .map(url -> BugReportImage.builder()
                            .bugReport(bugReport)
                            .imageUrl(url)
                            .build())
                    .collect(Collectors.toList());
            bugReport.setImages(images);
        }

        BugReport saved = bugReportRepository.save(bugReport);
        notifyAdminsAboutNewReport(saved);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public BugReportResponse createAuthenticatedReport(CreateBugReportRequest request, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));

        String safeDescription = request.getDescription() != null ? request.getDescription().trim() : null;

        BugReport bugReport = BugReport.builder()
                .reporterEmail(user.getEmail())
                .reporterName(user.getFullName())
                .userId(user.getId())
                .errorType(request.getErrorType())
                .description(safeDescription)
                .status(BugReportStatus.PENDING)
                .build();

        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            List<BugReportImage> images = request.getImageUrls().stream()
                    .map(url -> BugReportImage.builder()
                            .bugReport(bugReport)
                            .imageUrl(url)
                            .build())
                    .collect(Collectors.toList());
            bugReport.setImages(images);
        }

        BugReport saved = bugReportRepository.save(bugReport);
        notifyAdminsAboutNewReport(saved);
        return mapToResponse(saved);
    }

    private void notifyAdminsAboutNewReport(BugReport bugReport) {
        try {
            List<User> adminUsers = userRepository.findByRole(Role.ADMIN);
            if (adminUsers != null && !adminUsers.isEmpty()) {
                String errorTypeLabel = getErrorTypeLabel(bugReport.getErrorType());
                String message = "Hệ thống vừa nhận được Báo cáo sự cố mới từ " + bugReport.getReporterEmail() + " (" + errorTypeLabel + ").";
                for (User admin : adminUsers) {
                    try {
                        notificationService.saveAndSendNotification(
                                admin.getId(),
                                message,
                                "/admin/bug-reports"
                        );
                    } catch (Exception e) {
                        log.error("Lỗi khi gửi thông báo báo cáo mới cho Admin ID {}:", admin.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo báo cáo mới cho danh sách Admin:", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BugReportResponse> getReports(BugReportStatus status, Pageable pageable) {
        Page<BugReport> page;
        if (status != null) {
            page = bugReportRepository.findByStatus(status, pageable);
        } else {
            page = bugReportRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return page.map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public BugReportResponse getReportById(Long id) {
        BugReport bugReport = bugReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo lỗi với ID: " + id));
        return mapToResponse(bugReport);
    }

    @Override
    @Transactional
    public BugReportResponse updateReportStatus(Long id, UpdateBugReportStatusRequest request) {
        BugReport bugReport = bugReportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy báo cáo lỗi với ID: " + id));

        BugReportStatus oldStatus = bugReport.getStatus();
        BugReportStatus newStatus = request.getStatus();
        bugReport.setStatus(newStatus);
        BugReport updated = bugReportRepository.save(bugReport);

        // Kích hoạt gửi thông báo nếu có sự thay đổi trạng thái
        if (oldStatus != newStatus) {
            sendNotificationsForStatusUpdate(updated, newStatus);
        }

        return mapToResponse(updated);
    }

    private void sendNotificationsForStatusUpdate(BugReport bugReport, BugReportStatus newStatus) {
        String errorTypeLabel = getErrorTypeLabel(bugReport.getErrorType());
        String statusTitle;
        String statusMessage;
        String inAppMessage;

        if (newStatus == BugReportStatus.IN_PROGRESS) {
            statusTitle = "Sự cố đang được tiến hành xử lý";
            statusMessage = "Sự cố bạn báo cáo (Loại lỗi: " + errorTypeLabel + ") đã được tiếp nhận và bộ phận kỹ thuật đang tiến hành kiểm tra khắc phục.";
            inAppMessage = "Báo cáo sự cố (" + errorTypeLabel + ") của bạn đang được bộ phận kỹ thuật tiến hành xử lý.";
        } else if (newStatus == BugReportStatus.RESOLVED) {
            statusTitle = "Sự cố đã được giải quyết thành công";
            statusMessage = "Sự cố bạn báo cáo (Loại lỗi: " + errorTypeLabel + ") đã được hệ thống khắc phục hoàn toàn. Cảm ơn sự phản hồi quý báu của bạn!";
            inAppMessage = "Báo cáo sự cố (" + errorTypeLabel + ") của bạn đã được khắc phục hoàn toàn. Cảm ơn phản hồi của bạn!";
        } else {
            return; // Không gửi thông báo nếu đổi về PENDING
        }

        // 1. Gửi Email thông báo (dành cho cả Guest và Authenticated users)
        try {
            emailService.sendBugReportStatusEmail(
                    bugReport.getReporterEmail(),
                    bugReport.getReporterName(),
                    errorTypeLabel,
                    statusTitle,
                    statusMessage
            );
        } catch (Exception e) {
            log.error("Lỗi khi gửi email thông báo trạng thái báo cáo lỗi:", e);
        }

        // 2. Gửi In-app Bell Notification (dành riêng cho người dùng đã đăng nhập: Học sinh & Giáo viên)
        if (bugReport.getUserId() != null) {
            try {
                notificationService.saveAndSendNotification(
                        bugReport.getUserId(),
                        inAppMessage,
                        "/home"
                );
            } catch (Exception e) {
                log.error("Lỗi khi gửi thông báo nội bộ cho user id {}:", bugReport.getUserId(), e);
            }
        }
    }

    private String getErrorTypeLabel(BugErrorType type) {
        if (type == null) return "Khác";
        return switch (type) {
            case LOGIN_ACCOUNT -> "Lỗi đăng nhập / tài khoản";
            case UI_KATEX -> "Lỗi hiển thị giao diện / KaTeX";
            case SUBMISSION_PROBLEM -> "Lỗi không nộp bài / không tải đề";
            case PERFORMANCE -> "Lỗi tốc độ / không phản hồi";
            case OTHER -> "Khác";
        };
    }

    private BugReportResponse mapToResponse(BugReport entity) {
        List<String> imageUrls = entity.getImages() != null ?
                entity.getImages().stream().map(BugReportImage::getImageUrl).collect(Collectors.toList()) :
                new ArrayList<>();

        return BugReportResponse.builder()
                .id(entity.getId())
                .reporterEmail(entity.getReporterEmail())
                .reporterName(entity.getReporterName())
                .userId(entity.getUserId())
                .errorType(entity.getErrorType())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .imageUrls(imageUrls)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
