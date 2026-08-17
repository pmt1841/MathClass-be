package com.codegym.mathclass.bugreport.controller;

import com.codegym.mathclass.bugreport.dto.CreateBugReportRequest;
import com.codegym.mathclass.bugreport.dto.BugReportResponse;
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

import java.util.Map;

@Tag(name = "Bug Report", description = "APIs báo cáo sự cố và quản lý báo cáo lỗi hệ thống")
@RestController
@ApiVersion(1)
@RequestMapping
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;
    private final SupabaseStorageService supabaseStorageService;

    @Operation(summary = "Tải lên ảnh đính kèm báo cáo lỗi (Công khai)", description = "Upload file ảnh đính kèm cho báo cáo sự cố không cần token")
    @PostMapping("/bug-reports/public/upload-image")
    public ResponseEntity<Map<String, String>> uploadPublicBugReportImage(
            @RequestParam("file") MultipartFile file) throws Exception {
        String publicUrl = supabaseStorageService.uploadImage(file, "assignment_image");
        return ResponseEntity.ok(Map.of("imageUrl", publicUrl));
    }

    @Operation(summary = "Gửi báo cáo lỗi công khai (Chưa đăng nhập)", description = "Dành cho người dùng tại trang Login gửi phản hồi sự cố mà không cần token")
    @PostMapping("/bug-reports/public")
    public ResponseEntity<BugReportResponse> createPublicReport(
            @Valid @RequestBody CreateBugReportRequest request) {
        BugReportResponse response = bugReportService.createPublicReport(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Gửi báo cáo lỗi hệ thống (Đã đăng nhập)", description = "Dành cho Học sinh và Giáo viên gửi báo cáo lỗi từ giao diện chính")
    @PostMapping("/bug-reports")
    public ResponseEntity<BugReportResponse> createAuthenticatedReport(
            @Valid @RequestBody CreateBugReportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        BugReportResponse response = bugReportService.createAuthenticatedReport(request, userDetails.getUsername());
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
