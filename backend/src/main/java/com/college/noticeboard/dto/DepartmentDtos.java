package com.college.noticeboard.dto;

import com.college.noticeboard.entity.InstitutionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    /** institutionType defaults to ENGINEERING; programName and semesterCount default per institution. */
    public record DepartmentRequest(@NotBlank @Size(max = 120) String name,
                                    @NotBlank @Size(max = 20) String code,
                                    @Size(max = 500) String description,
                                    InstitutionType institutionType,
                                    @Size(max = 60) String programName,
                                    @Min(1) @Max(12) Integer semesterCount) {
    }

    public record DepartmentResponse(Long id, String name, String code, String description,
                                     InstitutionType institutionType, String programName, int semesterCount) {
    }
}
