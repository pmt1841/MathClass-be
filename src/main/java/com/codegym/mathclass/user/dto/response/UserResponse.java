package com.codegym.mathclass.user.dto.response;

import com.codegym.mathclass.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private long id;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Role role;
    private boolean isActive;
    private String avatarUrl;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd-MM-yyyy")
    private java.time.LocalDate dateOfBirth;
    private com.codegym.mathclass.user.entity.Gender gender;
}
