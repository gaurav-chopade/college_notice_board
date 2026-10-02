package com.college.noticeboard.entity;

/**
 * How a {@link NoticeTarget} row selects recipients. Every row is matched with the same rule:
 * each non-null field (role, department, semester, class, user) must equal the user's value.
 * The type documents intent and decides which fields are required.
 */
public enum TargetType {
    COLLEGE,
    ROLE,
    DEPARTMENT,
    SEMESTER,
    CLASS,
    ROLE_DEPARTMENT,
    SPECIFIC_USER
}
