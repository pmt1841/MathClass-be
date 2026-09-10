package com.codegym.mathclass.auth.strategy;

import com.codegym.mathclass.auth.entity.AuthType;
import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthStrategy<T> {

    UserInfoResponse authenticate(T request, HttpServletResponse response);

    boolean supports(AuthType authType);
}
