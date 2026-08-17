package com.codegym.mathclass.bugreport.controller;

import com.codegym.mathclass.bugreport.dto.BugReportResponse;
import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.SendOtpRequest;
import com.codegym.mathclass.bugreport.dto.UpdateBugReportStatusRequest;
import com.codegym.mathclass.bugreport.entity.BugReportStatus;
import com.codegym.mathclass.bugreport.service.BugReportService;
import com.codegym.mathclass.common.annotation.ApiVersion;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.utils.SupabaseStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Tag(name = "Bug Report", description = "APIs báo cáo sự cố và quản lý báo cáo lỗi hệ thống")
@RestController
@ApiVersion(1)
@RequestMapping
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;
    private final SupabaseStorageService supabaseStorageService;

    private String getClientIp(HttpServletRequest httpRequest) {
        if (httpRequest == null) return "127.0.0.1";
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        for (String header : headers) {
            String ip = httpRequest.getHeader(header);
            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                return ip.split(",")[0].trim();
            }
        }
        return httpRequest.getRemoteAddr();
    }

    @Operation(summary = "Tải lên ảnh đính kèm báo cáo lỗi (Công khai)", description = "Upload file ảnh đính kèm cho báo cáo sự cố không cần token")
    @PostMapping("/bug-reports/public/upload-image")
    public ResponseEntity<Map<String, String>> uploadPublicBugReportImage(
            @RequestParam("file") MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new com.codegym.mathclass.exception.BadRequestException("Tập tin đính kèm không được để trống");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new com.codegym.mathclass.exception.BadRequestException("Dung lượng ảnh đính kèm vượt quá 5MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new com.codegym.mathclass.exception.BadRequestException("Chỉ chấp nhận tập tin định dạng hình ảnh (PNG, JPG, JPEG, WEBP)");
        }
        String publicUrl = supabaseStorageService.uploadImage(file, "assignment_image");
        return ResponseEntity.ok(Map.of("imageUrl", publicUrl));
    }

    @Operation(summary = "Gửi mã OTP xác thực báo cáo lỗi công khai", description = "Gửi mã OTP 6 số về email của người dùng chưa đăng nhập trước khi gửi báo cáo lỗi")
    @PostMapping("/bug-reports/public/send-otp")
    public ResponseEntity<Map<String, String>> sendPublicReportOtp(
            @Valid @RequestBody SendOtpRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        bugReportService.sendPublicReportOtp(request, clientIp);
        return ResponseEntity.ok(Map.of("message", "Mã OTP đã được gửi đến email của bạn. Vui lòng kiểm tra hộp thư (kể cả thư mục Spam)."));
    }

    @Operation(summary = "Gửi báo cáo lỗi công khai (Chưa đăng nhập)", description = "Dành cho người dùng tại trang Login gửi phản hồi sự cố mà không cần token")
    @PostMapping("/bug-reports/public")
    public ResponseEntity<BugReportResponse> createPublicReport(
            @Valid @RequestBody CreateBugReportRequest request,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        BugReportResponse response = bugReportService.createPublicReport(request, clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Gửi báo cáo lỗi hệ thống (Đã đăng nhập)", description = "Dành cho Học sinh và Giáo viên gửi báo cáo lỗi từ giao diện chính")
    @PostMapping("/bug-reports")
    public ResponseEntity<BugReportResponse> createAuthenticatedReport(
            @Valid @RequestBody CreateBugReportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        BugReportResponse response = bugReportService.createAuthenticatedReport(request, userDetails.getUsername(), clientIp);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Lấy danh sách báo cáo lỗi (Admin)", description = "Lấy danh sách báo cáo phân trang dành cho Quản trị viên")
    @GetMapping("/admin/bug-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<BugReportResponse>> getReports(
            @RequestParam(required = false) BugReportStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<BugReportResponse> reports = bugReportService.getReports(status, pageable);
        return ResponseEntity.ok(reports);
    }

    @Operation(summary = "Lấy chi tiết báo cáo lỗi (Admin)", description = "Xem thông tin chi tiết báo cáo lỗi kèm danh sách ảnh đính kèm")
    @GetMapping("/admin/bug-reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BugReportResponse> getReportById(
            @PathVariable Long id) {
        BugReportResponse report = bugReportService.getReportById(id);
        return ResponseEntity.ok(report);
    }

    @Operation(summary = "Cập nhật trạng thái báo cáo lỗi (Admin)", description = "Admin thay đổi trạng thái xử lý của báo cáo (PENDING, IN_PROGRESS, RESOLVED)")
    @PatchMapping("/admin/bug-reports/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BugReportResponse> updateReportStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBugReportStatusRequest request) {
        BugReportResponse updated = bugReportService.updateReportStatus(id, request);
        return ResponseEntity.ok(updated);
    }
}
