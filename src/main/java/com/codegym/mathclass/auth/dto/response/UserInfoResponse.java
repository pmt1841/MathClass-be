package com.codegym.mathclass.auth.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UserInfoResponse {
    private long id;
    private String email;
    private String fullName;
    private String userRole;
    private String avatarUrl;
    private List<String> permissions;
}
