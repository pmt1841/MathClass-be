package com.codegym.mathclass.security.services;

import java.io.Serial;
import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.codegym.mathclass.user.entity.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@Getter
public class CustomUserDetails implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private long id;

    private String fullName;

    private String email;

    @JsonIgnore
    private String password;

    private boolean isActive;

    private String avatarUrl;

    private Collection<? extends GrantedAuthority> authorities;

    public static CustomUserDetails build(User user, List<String> permissions) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        
        if (permissions != null) {
            permissions.forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        }

        return new CustomUserDetails(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPassword(),
                user.isActive(),
                user.getAvatarUrl(),
                authorities);
    }

    /**
     * Tài khoản chưa bị hết hạn (Mặc định luôn là true trong hệ thống này).
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Kiểm tra tài khoản có đang KHÔNG bị khóa hay không.
     * Trả về true nếu tài khoản hoạt động (isActive = true), ngược lại trả về false nếu đã bị Admin khóa (isActive = false).
     */
    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    /**
     * Mật khẩu/Credentials chưa bị hết hạn.
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Kiểm tra tài khoản có được kích hoạt hay không.
     * Trả về true nếu tài khoản đã được kích hoạt và chưa bị khóa.
     */
    @Override
    public boolean isEnabled() {
        return isActive;
    }

    @Override
    public String getUsername() {
        return email;
    }

    // So sánh đối tượng
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CustomUserDetails user = (CustomUserDetails) o;
        return java.util.Objects.equals(id, user.id);
    }
}
