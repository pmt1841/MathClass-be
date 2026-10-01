package com.codegym.mathclass.assignment.repository;

/**
 * Projection đại diện cho cặp thông tin (title, classCode) của phiếu bài tập đã xuất bản.
 * Thay thế raw List<Object[]> để đảm bảo an toàn kiểu dữ liệu (Type-safety).
 */
public interface SheetPublishedClassProjection {
    String getTitle();
    String getClassCode();
}
