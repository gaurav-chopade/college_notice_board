package com.college.noticeboard.entity;

/** Application roles. Stored as a string on {@link User}; exposed to Spring Security as ROLE_xxx. */
public enum Role {
    SUPER_ADMIN,
    PRINCIPAL,
    HOD,
    FACULTY,
    STUDENT
}
