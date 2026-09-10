package com.codegym.mathclass.storage.service;

import com.codegym.mathclass.storage.dto.StoragePolicy;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface StorageService {

    String upload(MultipartFile file, StoragePolicy policy) throws IOException;

    String upload(byte[] fileData, String fileName, String contentType, StoragePolicy policy);

    void delete(String fileUrl);
}
