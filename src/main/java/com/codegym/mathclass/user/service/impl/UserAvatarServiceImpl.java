package com.codegym.mathclass.user.service.impl;

import com.codegym.mathclass.exception.BadRequestException;
import com.codegym.mathclass.storage.dto.StoragePolicy;
import com.codegym.mathclass.storage.service.StorageService;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.repository.UserRepository;
import com.codegym.mathclass.user.service.UserAvatarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class UserAvatarServiceImpl implements UserAvatarService {

    private final UserRepository userRepository;
    private final StorageService storageService;

    @Override
    @Transactional
    public String uploadAvatar(Long userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng với ID: " + userId));

        if (user.getProvider() == Provider.GOOGLE) {
            throw new BadRequestException("Không thể thay đổi ảnh đại diện cho tài khoản liên kết Google");
        }

        String oldAvatarUrl = user.getAvatarUrl();

        try {
            String avatarUrl = storageService.upload(file, StoragePolicy.AVATAR);
            user.setAvatarUrl(avatarUrl);
            userRepository.save(user);

            // Tức thời dọn dẹp avatar cũ nếu là ảnh thuộc hệ thống
            if (oldAvatarUrl != null && !oldAvatarUrl.isBlank()) {
                try {
                    storageService.delete(oldAvatarUrl);
                } catch (Exception e) {
                    log.warn("Không thể xóa avatar cũ [{}]: {}", oldAvatarUrl, e.getMessage());
                }
            }

            return avatarUrl;
        } catch (IOException e) {
            log.error("Lỗi khi upload ảnh đại diện cho user {}: {}", userId, e.getMessage());
            throw new BadRequestException("Lỗi khi upload ảnh đại diện: " + e.getMessage());
        }
    }
}
