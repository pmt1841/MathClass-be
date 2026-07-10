# Báo cáo Đánh giá Mã nguồn (Code & Architecture Reviews)

Thư mục này được sử dụng để lưu trữ các tài liệu đánh giá chất lượng mã nguồn, kiến trúc và tính bảo mật của dự án **MathClass-service**.

## Danh sách Báo cáo Đánh giá (Review Reports)

*   **[Báo cáo Đánh giá Bảo mật (Security Review)](security-review.md)** - Đánh giá về tính bảo mật của mã nguồn, kiểm soát truy cập (BOLA/IDOR) và các lỗ hổng tiêm mã độc (LaTeX Injection). *Cập nhật ngày: 10/07/2026*

## Hướng dẫn Tạo Báo cáo Đánh giá mới
Khi thực hiện đánh giá mã nguồn hoặc tính năng mới, hãy tạo một tệp tin Markdown trong thư mục này với định dạng đặt tên:
`reviews/[loai-review]-review.md`

Ví dụ:
*   `reviews/code-quality-review.md`
*   `reviews/performance-review.md`

## Quy trình Xác minh & Cập nhật Sau khi Sửa lỗi (Verification & Follow-up)
Khi các phát hiện trong báo cáo review được khắc phục, chúng ta sẽ quản lý và đánh giá việc sửa đổi theo một trong hai cách dưới đây:

### Cách 1: Cập nhật trực tiếp trạng thái trong Báo cáo Gốc (Khuyên dùng)
Trong tệp review ban đầu (ví dụ: `security-review.md`), mỗi mục phát hiện sẽ được bổ sung thông tin trạng thái:
*   `[ ] 🔴 Open`: Chưa xử lý.
*   `[ ] 🟡 In Progress`: Đang xử lý.
*   `[x] 🟢 Resolved`: Đã khắc phục thành công.
*   **Yêu cầu:** Đi kèm với nhãn `🟢 Resolved` cần ghi rõ:
    *   Mô tả ngắn gọn cách sửa đổi.
    *   Liên kết đến commit/PR chứa mã nguồn sửa đổi.
    *   Câu lệnh chạy test tự động (regression test) để xác minh lỗi đã được vá.

### Cách 2: Tạo Báo cáo Xác minh độc lập (Validation Report)
Khi sửa đổi hàng loạt hoặc cần kiểm duyệt độc lập (QA/Security team), hãy tạo một tệp riêng với định dạng:
`reviews/[loai-review]-validation.md` (Ví dụ: `reviews/security-validation.md`) để liệt kê chi tiết:
*   Tên lỗi tương ứng trong báo cáo gốc.
*   Trạng thái xác minh (Pass / Fail).
*   Chứng cứ kiểm thử (Mã kiểm thử mới được viết, kết quả test logs...).

*Lưu ý: Không viết các đường dẫn tuyệt đối của môi trường local vào trong tài liệu đặc tả hoặc báo cáo review.*
