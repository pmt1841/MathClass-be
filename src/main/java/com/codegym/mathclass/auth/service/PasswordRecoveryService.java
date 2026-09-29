package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.request.ForgotPasswordRequest;
import com.codegym.mathclass.auth.dto.request.ResetPasswordRequest;
import com.codegym.mathclass.auth.dto.response.MessageResponse;

public interface PasswordRecoveryService {

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);
}
