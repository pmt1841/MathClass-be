package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.response.UserResponse;

public interface UserService {
    UserResponse getUserProfile(Long id);
}
