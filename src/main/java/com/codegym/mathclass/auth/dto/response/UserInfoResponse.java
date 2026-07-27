package com.codegym.mathclass.auth.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserInfoResponse {
    private long id;
    private String email;
    private String fullName;
    private String userRole;
    private String avatarUrl;
    private List<String> permissions;
    private String token;

    public UserInfoResponse(long id, String email, String fullName, String userRole, String avatarUrl, List<String> permissions) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.userRole = userRole;
        this.avatarUrl = avatarUrl;
        this.permissions = permissions;
        this.token = null;
    }
}

