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
    private Long id;
    private String userName;

    public JwtResponse(String accessToken, Long id, String userName) {
        this.token = accessToken;
        this.id = id;
        this.userName = userName;
    }
}
