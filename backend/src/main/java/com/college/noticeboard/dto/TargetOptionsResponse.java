package com.college.noticeboard.dto;

import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.TargetType;

import java.util.List;

/** Tells the UI which targeting controls to show; the backend re-checks every target on save. */
public record TargetOptionsResponse(List<TargetType> allowedTypes, List<Role> allowedRoles,
                                    Long fixedDepartmentId, String fixedDepartmentName,
                                    boolean requiresApproval) {
}
