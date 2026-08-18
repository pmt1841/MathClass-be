package com.codegym.mathclass.auth.dto.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TwoFactorConfirmResponse {
    private UserInfoResponse userInfo;
    private List<String> backupCodes;
    private String message;
}
