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
import com.codegym.mathclass.utils.EmailValidatorUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.codegym.mathclass.bugreport.dto.SendOtpRequest;

import com.codegym.mathclass.exception.TooManyRequestsException;

@Service
@RequiredArgsConstructor
@Slf4j
public class BugReportServiceImpl implements BugReportService {

    private final BugReportRepository bugReportRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;

    private static class OtpInfo {
        final String otpCode;
        final long expiryTimeMs;

        OtpInfo(String otpCode, long expiryTimeMs) {
            this.otpCode = otpCode;
            this.expiryTimeMs = expiryTimeMs;
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, OtpInfo> otpCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> emailLastReportTimeMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<String, java.util.List<Long>> ipReportHistoryMap = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 * * * *")
    public void cleanupExpiredCacheData() {
        long now = System.currentTimeMillis();
        long window24Hour = 24 * 60 * 60 * 1000L;

        otpCache.entrySet().removeIf(entry -> now > entry.getValue().expiryTimeMs);
        emailLastReportTimeMap.entrySet().removeIf(entry -> (now - entry.getValue()) > 10 * 60 * 1000L);
        ipReportHistoryMap.entrySet().removeIf(entry -> {
            java.util.List<Long> timestamps = entry.getValue();
            if (timestamps != null) {
                synchronized (timestamps) {
                    timestamps.removeIf(ts -> (now - ts) > window24Hour);
                    return timestamps.isEmpty();
                }
            }
            return true;
        });
    }

    private void checkEmailRateLimit(String email) {
        Long lastReportTime = emailLastReportTimeMap.get(email);
        if (lastReportTime != null) {
            long elapsedMs = System.currentTimeMillis() - lastReportTime;
            long cooldownMs = 60 * 1000; // 60 giây
            if (elapsedMs < cooldownMs) {
                long remainingSeconds = (long) Math.ceil((cooldownMs - elapsedMs) / 1000.0);
                throw new BadRequestException("Email '" + email + "' vừa gửi báo cáo sự cố gần đây. Vui lòng chờ " + remainingSeconds + " giây nữa trước khi gửi báo cáo tiếp theo.");
            }
        }
    }

    private void checkIpRateLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        long now = System.currentTimeMillis();
        long window10Min = 10 * 60 * 1000L;
        long window24Hour = 24 * 60 * 60 * 1000L;

        java.util.List<Long> timestamps = ipReportHistoryMap.get(clientIp);
        if (timestamps != null) {
            synchronized (timestamps) {
                timestamps.removeIf(ts -> (now - ts) > window24Hour);

                long count10Min = timestamps.stream().filter(ts -> (now - ts) <= window10Min).count();
                if (count10Min >= 2) {
                    throw new TooManyRequestsException("Địa chỉ IP của bạn đã gửi 2 báo cáo sự cố trong vòng 10 phút. Vui lòng chờ 10 phút trước khi gửi lại.");
                }

                if (timestamps.size() >= 5) {
                    throw new TooManyRequestsException("Địa chỉ IP của bạn đã vượt quá giới hạn 5 báo cáo sự cố / 24 giờ. Vui lòng thử lại vào ngày mai.");
                }
            }
        }
    }

    private void recordIpReport(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) return;
        long now = System.currentTimeMillis();
        ipReportHistoryMap.compute(clientIp, (ip, list) -> {
            if (list == null) {
                list = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
            }
            list.add(now);
            return list;
        });
    }

    @Override
    public void sendPublicReportOtp(SendOtpRequest request, String clientIp) {
        String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) {
            throw new BadRequestException("Email không được để trống");
        }

        checkIpRateLimit(clientIp);
        checkEmailRateLimit(email);

        if (!EmailValidatorUtils.hasValidMxRecord(email)) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : email;
            throw new BadRequestException("Tên miền email '" + domain + "' không tồn tại hoặc không thể nhận thư. Vui lòng kiểm tra lại địa chỉ email.");
        }

        String otpCode = String.format("%06d", new java.util.Random().nextInt(1000000));
        long expiryTimeMs = System.currentTimeMillis() + (5 * 60 * 1000); // 5 phút

        otpCache.put(email, new OtpInfo(otpCode, expiryTimeMs));
        emailService.sendBugReportOtpEmail(email, otpCode);
        recordIpReport(clientIp);
        log.info("Mã OTP xác thực báo cáo lỗi đã được gửi đến email {} từ IP {}", email, clientIp);
    }

    @Override
    @Transactional
    public BugReportResponse createPublicReport(CreateBugReportRequest request, String clientIp) {
        String email = request.getReporterEmail() != null ? request.getReporterEmail().trim().toLowerCase() : "";
        if (email.isEmpty()) {
            throw new BadRequestException("Email không được để trống đối với báo cáo công khai");
        }

        // 1. Honeypot Trap Check: Nếu trường bẫy ẩn website có dữ liệu -> Đánh lừa Spambot trả về HTTP 200 giả vờ thành công
        if (request.getWebsite() != null && !request.getWebsite().trim().isEmpty()) {
            log.warn("Honeypot trap triggered by spambot from IP {}! Fake 200 OK success returned.", clientIp);
            return BugReportResponse.builder()
                    .id(0L)
                    .reporterEmail(email)
                    .reporterName(request.getReporterName())
                    .errorType(request.getErrorType())
                    .status(BugReportStatus.PENDING)
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
        }

        // 2. Time-based Submission Check: Nếu thời gian điền form < 3 giây -> Chặn script tự động
        if (request.getFormLoadedAt() != null) {
            long duration = System.currentTimeMillis() - request.getFormLoadedAt();
            if (duration < 3000) {
                log.warn("Form submitted too fast ({}ms) from IP {}, suspected bot.", duration, clientIp);
                throw new BadRequestException("Thao tác gửi báo cáo quá nhanh (dưới 3 giây). Vui lòng kiểm tra lại thông tin.");
            }
        }

        // 3. IP-based Rate Limiting & Email Rate Limiting
        checkIpRateLimit(clientIp);
        checkEmailRateLimit(email);

        if (!EmailValidatorUtils.hasValidMxRecord(email)) {
            String domain = email.contains("@") ? email.substring(email.indexOf("@") + 1) : email;
            throw new BadRequestException("Tên miền email '" + domain + "' không tồn tại hoặc không thể nhận thư. Vui lòng kiểm tra lại địa chỉ email.");
        }

        String inputOtp = request.getOtp() != null ? request.getOtp().trim() : "";
        if (inputOtp.isEmpty()) {
            throw new BadRequestException("Vui lòng nhập mã OTP xác thực 6 số đã gửi về email của bạn");
        }

        OtpInfo cachedOtp = otpCache.get(email);
        if (cachedOtp == null || System.currentTimeMillis() > cachedOtp.expiryTimeMs) {
            otpCache.remove(email);
            throw new BadRequestException("Mã OTP chưa được gửi hoặc đã hết hạn (hiệu lực 5 phút). Vui lòng bấm 'Gửi mã OTP' để nhận mã mới.");
        }

        if (!cachedOtp.otpCode.equals(inputOtp)) {
            throw new BadRequestException("Mã OTP nhập vào không chính xác. Vui lòng kiểm tra lại hòm thư.");
        }

        // Xác thực OTP thành công -> Xóa OTP khỏi đệm, ghi nhận mốc thời gian cho Email & IP
        otpCache.remove(email);
        emailLastReportTimeMap.put(email, System.currentTimeMillis());
        recordIpReport(clientIp);

        String safeName = request.getReporterName() != null ? request.getReporterName().trim() : null;
        String safeDescription = request.getDescription() != null ? request.getDescription().trim() : null;

        BugReport bugReport = BugReport.builder()
                .reporterEmail(email)
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
    public BugReportResponse createAuthenticatedReport(CreateBugReportRequest request, String username, String clientIp) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người dùng: " + username));

        String email = user.getEmail().toLowerCase();

        // 1. Honeypot Trap Check: Nếu trường bẫy ẩn website có dữ liệu -> Đánh lừa Spambot trả về HTTP 200 giả vờ thành công
        if (request.getWebsite() != null && !request.getWebsite().trim().isEmpty()) {
            log.warn("Honeypot trap triggered by bot (authenticated user {}) from IP {}!", username, clientIp);
            return BugReportResponse.builder()
                    .id(0L)
                    .reporterEmail(user.getEmail())
                    .reporterName(user.getFullName())
                    .userId(user.getId())
                    .errorType(request.getErrorType())
                    .status(BugReportStatus.PENDING)
                    .createdAt(java.time.LocalDateTime.now())
                    .updatedAt(java.time.LocalDateTime.now())
                    .build();
        }

        // 2. Time-based Submission Check: Nếu thời gian điền form < 3 giây -> Chặn script tự động
        if (request.getFormLoadedAt() != null) {
            long duration = System.currentTimeMillis() - request.getFormLoadedAt();
            if (duration < 3000) {
                log.warn("Authenticated report submitted too fast ({}ms) from user {}, suspected script.", duration, username);
                throw new BadRequestException("Thao tác gửi báo cáo quá nhanh (dưới 3 giây). Vui lòng kiểm tra lại thông tin.");
            }
        }

        // 3. IP-based Rate Limiting & Email Rate Limiting (60s cooldown per email)
        checkIpRateLimit(clientIp);
        checkEmailRateLimit(email);

        // Cập nhật mốc thời gian gửi báo cáo gần nhất cho Email & IP
        emailLastReportTimeMap.put(email, System.currentTimeMillis());
        recordIpReport(clientIp);

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
