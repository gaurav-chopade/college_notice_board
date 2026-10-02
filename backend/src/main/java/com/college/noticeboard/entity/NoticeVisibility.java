package com.college.noticeboard.entity;

/**
 * PRIVATE notices reach college members through targets (role, department, class...).
 * PUBLIC notices are also readable by anyone, without signing in, once they are published
 * and until they expire. They may additionally have targets to notify members.
 */
public enum NoticeVisibility {
    PRIVATE,
    PUBLIC
}
