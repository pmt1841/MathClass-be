package com.codegym.mathclass.user.mapper;

import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final PermissionCacheService permissionCacheService;

    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .role(user.getRole())
                .isActive(user.isActive())
                .avatarUrl(user.getAvatarUrl())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .provider(user.getProvider())
                .permissions(permissionCacheService.getPermissionsByRole(user.getRole()))
                .build();
    }
}
