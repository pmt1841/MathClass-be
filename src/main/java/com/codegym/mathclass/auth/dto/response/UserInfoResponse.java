package com.codegym.mathclass.auth.dto.response;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInfoResponse {
    private long id;
    private String email;
    private String fullName;
    private String userRole;
    private String avatarUrl;
    private List<String> permissions;
    private String token;

    // 2FA Fields
    private Boolean is2faRequired;
    private Boolean isSetupRequired;
    private String preAuthToken;
    private String message;

    public UserInfoResponse(long id, String email, String fullName, String userRole, String avatarUrl, List<String> permissions) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.userRole = userRole;
        this.avatarUrl = avatarUrl;
        this.permissions = permissions;
        this.token = null;
    }

    public UserInfoResponse(long id, String email, String fullName, String userRole, String avatarUrl, List<String> permissions, String token) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.userRole = userRole;
        this.avatarUrl = avatarUrl;
        this.permissions = permissions;
        this.token = token;
    }
}
