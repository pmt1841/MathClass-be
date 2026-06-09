package com.codegym.mathclass.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
    private long id;
    private String email;
    private String fullName;
    private String userRole;

    public JwtResponse(String accessToken, long id, String email, String fullName, String role) {
        this.token = accessToken;
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.userRole = role;
    }
}
