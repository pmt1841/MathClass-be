package com.codegym.mathclass.auth.service;

import com.codegym.mathclass.auth.dto.response.UserInfoResponse;
import com.codegym.mathclass.security.services.CustomUserDetails;
import com.codegym.mathclass.user.entity.User;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthSessionService {

    UserInfoResponse issueAuthSession(User user, boolean rememberMe, HttpServletResponse response);

    UserInfoResponse issueAuthSession(CustomUserDetails userDetails, boolean rememberMe, HttpServletResponse response);

    void setRefreshAccessCookie(User user, HttpServletResponse response);

    void clearSessionCookies(HttpServletResponse response);
}
