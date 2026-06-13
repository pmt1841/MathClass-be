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
}
