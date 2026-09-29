package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.SignupRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;

import com.codegym.mathclass.user.entity.Role;
import com.codegym.mathclass.user.entity.User;

public interface UserRegistrationService {

    MessageResponse registerUser(SignupRequest signUpRequest);

    MessageResponse verifyUser(String token);

    User registerOAuth2User(String email, String fullName, String pictureUrl, Role role);
}
