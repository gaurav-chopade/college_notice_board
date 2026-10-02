package com.college.noticeboard.dto;

import com.college.noticeboard.dto.UserDtos.UserLookupResponse;
import com.college.noticeboard.dto.UserDtos.UserResponse;
import com.college.noticeboard.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getDepartmentId(), u.getDepartment() == null ? null : u.getDepartment().getName(),
                u.getSemester(), u.getClassName(), u.getPhone(), u.isActive(), u.getStatus(),
                u.getStatusReason(), u.getCreatedAt(), u.getInstitutionType());
    }

    public static UserLookupResponse toLookup(User u) {
        return new UserLookupResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole(),
                u.getDepartment() == null ? null : u.getDepartment().getName(), u.getSemester(), u.getClassName());
    }
}
