package com.codegym.mathclass.user.entity;

public enum Role {
    ADMIN("Quản trị viên"),
    TEACHER("Giáo viên"),
    STUDENT("Học sinh");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
