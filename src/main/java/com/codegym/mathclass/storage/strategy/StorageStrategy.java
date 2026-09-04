package com.codegym.mathclass.storage.strategy;

import com.codegym.mathclass.storage.dto.StoragePolicy;

/**
 * StorageStrategy định nghĩa hợp đồng chuẩn cho các nhà cung cấp dịch vụ lưu trữ tệp.
 */
public interface StorageStrategy {

    /**
     * Tải tệp lên dịch vụ lưu trữ.
     *
     * @close fileData mảng byte của tệp
     * @param fileName tên tệp ban đầu
     * @param contentType định dạng media (MIME)
     * @param policy chính sách lưu trữ áp dụng
     * @return URL truy cập công khai của tệp
     */
    String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy);

    /**
     * Xóa tệp từ dịch vụ lưu trữ theo URL.
     *
     * @param fileUrl URL công khai của tệp cần xóa
     */
    void delete(String fileUrl);
}
