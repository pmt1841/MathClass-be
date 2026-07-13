# Tổng Quan Dự Án MathClass (Backend)

Chào mừng bạn đến với module Backend của dự án **MathClass**. Hệ thống quản lý học tập (LMS) dành riêng cho Toán trực tuyến.
Tài liệu này đóng vai trò hướng dẫn các thành viên mới trong team Backend nắm bắt nhanh dự án.

## 1. Mục tiêu

Cung cấp RESTful API, quản lý dữ liệu, phân quyền và xử lý nghiệp vụ chính:

- Xác thực và phân quyền bằng JWT & Google OAuth2.
- Xử lý các nghiệp vụ liên quan đến User, Classroom, Assignment, Submission, Dashboard.
- Bảo mật dữ liệu Toán học (Sanitize LaTeX tránh injection).

## 2. Kiến trúc (Architecture)

Dự án được xây dựng theo kiến trúc phân tầng (Layered Architecture):

- **Cơ sở dữ liệu:** PostgreSQL 16 (Dữ liệu quan hệ & `jsonb` cho tọa độ vẽ hình).
- **Core Framework:** Spring Boot 3, Java 21.
- **ORM:** Spring Data JPA / Hibernate.
- **Bảo mật:** Spring Security.

## 3. Các tài liệu quan trọng

Hãy đọc theo thứ tự sau để nhanh chóng bắt tay vào code:

1. [Hướng dẫn Cài đặt Môi trường (Setup Guide)](02-setup-guide.md)
2. [Quy chuẩn và Hướng dẫn Code (Backend Guide)](03-backend-guide.md)
3. [Tài liệu API (API Reference)](04-api-reference.md)
