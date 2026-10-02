package com.codegym.mathclass.user.service;

import com.codegym.mathclass.user.dto.request.ChangePasswordRequest;
import com.codegym.mathclass.user.dto.request.SetPasswordRequest;

public interface UserCredentialService {
    void changePassword(Long userId, ChangePasswordRequest request);
    void sendSetPasswordOtp(Long userId);
    void setPassword(Long userId, SetPasswordRequest request);
}
