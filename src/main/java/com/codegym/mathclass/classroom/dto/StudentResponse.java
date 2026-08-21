package com.codegym.mathclass.classroom.dto;

import com.codegym.mathclass.user.entity.User;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private boolean isOnline;
    private LocalDateTime lastActiveAt;

    public static StudentResponse fromEntity(User user) {
        boolean online = false;
        if (user.getLastActiveAt() != null) {
            LocalDateTime lastActive = user.getLastActiveAt();
            LocalDateTime nowLocal = LocalDateTime.now();
            LocalDateTime nowUtc = LocalDateTime.now(java.time.ZoneOffset.UTC);

            long diffMinutesLocal = Math.abs(java.time.Duration.between(lastActive, nowLocal).toMinutes());
            long diffMinutesUtc = Math.abs(java.time.Duration.between(lastActive, nowUtc).toMinutes());

            online = diffMinutesLocal <= 15 || diffMinutesUtc <= 15;
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
