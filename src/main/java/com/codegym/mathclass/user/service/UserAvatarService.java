package com.codegym.mathclass.user.service;

import org.springframework.web.multipart.MultipartFile;

public interface UserAvatarService {
    String uploadAvatar(Long userId, MultipartFile file);
}
