package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.LoginRequest;
import com.codegym.mathclass.auth.dto.request.SignupRequest;
import org.springframework.http.ResponseEntity;

public interface AuthService {
    ResponseEntity<?> authenticateUser(LoginRequest loginRequest);

    ResponseEntity<?> logoutUser();

    ResponseEntity<?> registerUser(SignupRequest signUpRequest);

    ResponseEntity<?> verifyUser(String token);
}
