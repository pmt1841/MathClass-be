package com.codegym.mathclass.user.mapper;

import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.dto.request.UpdateProfileRequest;
import com.codegym.mathclass.user.dto.response.UserResponse;
import com.codegym.mathclass.user.entity.Provider;
import com.codegym.mathclass.user.entity.User;
import com.codegym.mathclass.user.service.PermissionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public UserInfoResponse toUserInfoResponse(CustomUserDetails userDetails) {
        if (userDetails == null) {
            return null;
        }
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .map(r -> r.replace("ROLE_", ""))
                .orElse("");

        List<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .toList();

        return new UserInfoResponse(
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getFullName(),
                role,
                userDetails.getAvatarUrl(),
                permissions);
    }

    public UserInfoResponse toUserInfoResponse(CustomUserDetails userDetails, String token) {
        if (userDetails == null) {
            return null;
        }
        String role = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .findFirst()
                .map(r -> r.replace("ROLE_", ""))
                .orElse("");

        List<String> permissions = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .toList();

        return new UserInfoResponse(
                userDetails.getId(),
                userDetails.getEmail(),
                userDetails.getFullName(),
                role,
                userDetails.getAvatarUrl(),
                permissions,
                token);
    }

    public void updateUserFromRequest(User user, UpdateProfileRequest request) {
        if (user == null || request == null) {
            return;
        }
        if (user.getProvider() != Provider.GOOGLE) {
            user.setFullName(request.getFullName());
            if (request.getAvatarUrl() != null && !request.getAvatarUrl().isEmpty()) {
                user.setAvatarUrl(request.getAvatarUrl());
            }
        }
        user.setPhoneNumber(request.getPhoneNumber());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender());
    }
}

