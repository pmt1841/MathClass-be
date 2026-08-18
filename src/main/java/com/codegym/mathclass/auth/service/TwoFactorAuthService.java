package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.TwoFactorConfirmRequest;
import com.codegym.mathclass.auth.dto.request.TwoFactorVerifyRequest;
import com.codegym.mathclass.auth.dto.response.TwoFactorConfirmResponse;
import com.codegym.mathclass.auth.dto.response.TwoFactorSetupResponse;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface TwoFactorAuthService {
    TwoFactorSetupResponse initiateSetup(String authHeader);
    TwoFactorConfirmResponse confirmSetup(TwoFactorConfirmRequest request, String authHeader, HttpServletResponse response);
    UserInfoResponse verifyLogin(TwoFactorVerifyRequest request, String authHeader, HttpServletResponse response);
}
