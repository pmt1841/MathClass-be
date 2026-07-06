package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.JwtResponse;
import com.codegym.mathclass.auth.dto.response.MessageResponse;

public interface AuthService {
    JwtResponse authenticateUser(LoginRequest loginRequest);

    MessageResponse logoutUser();

    MessageResponse registerUser(SignupRequest signUpRequest);

    MessageResponse verifyUser(String token);

    JwtResponse authenticateWithGoogle(com.codegym.mathclass.auth.dto.request.GoogleAuthRequest request);
}
