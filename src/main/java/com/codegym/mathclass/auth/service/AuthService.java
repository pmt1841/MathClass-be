package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;
import com.codegym.mathclass.auth.dto.request.GoogleAuthRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    UserInfoResponse authenticateUser(LoginRequest loginRequest, HttpServletResponse response);

    MessageResponse logoutUser(HttpServletRequest request, HttpServletResponse response);

    MessageResponse registerUser(SignupRequest signUpRequest);

    MessageResponse verifyUser(String token);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

    UserInfoResponse authenticateWithGoogle(GoogleAuthRequest request, HttpServletResponse response);

    MessageResponse refreshToken(HttpServletRequest request, HttpServletResponse response);
}
