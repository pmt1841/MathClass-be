package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {
    private long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String avatarUrl;

    @JsonProperty("isOnline")
    private boolean isOnline;

    private LocalDateTime lastActiveAt;

    public static StudentResponse fromEntity(User user) {
        boolean online = false;
        if (user.getLastActiveAt() != null) {
            LocalDateTime lastActive = user.getLastActiveAt();
            LocalDateTime nowLocal = LocalDateTime.now();
            LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);

            long diffLocal = Math.abs(java.time.Duration.between(lastActive, nowLocal).toMinutes());
            long diffUtc = Math.abs(java.time.Duration.between(lastActive, nowUtc).toMinutes());

            long diffLocalWithOffset = Math.abs(diffLocal - 420);
            long diffUtcWithOffset = Math.abs(diffUtc - 420);

            online = diffLocal <= 30 || diffUtc <= 30 || diffLocalWithOffset <= 30 || diffUtcWithOffset <= 30;
        }

        return StudentResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .isOnline(online)
                .lastActiveAt(user.getLastActiveAt())
                .build();
    }
}
