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
        return fromEntity(user, false);
    }

    public static StudentResponse fromEntity(User user, boolean isSocketOnline) {
        boolean online = isSocketOnline || (user.getLastActiveAt() != null 
                && user.getLastActiveAt().isAfter(LocalDateTime.now().minusMinutes(5)));

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
