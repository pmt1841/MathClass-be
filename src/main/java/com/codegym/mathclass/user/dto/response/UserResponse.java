package com.codegym.mathclass.user.dto.response;

import com.codegym.mathclass.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDate;
import com.codegym.mathclass.user.entity.Gender;
import com.codegym.mathclass.user.entity.Provider;

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
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate dateOfBirth;
    private Gender gender;
    private java.util.List<String> permissions;
    private Provider provider;
}
