package com.college.noticeboard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(@NotBlank @Size(max = 80) String name, @Size(max = 300) String description, Boolean active) {
    }

    public record CategoryResponse(Long id, String name, String description, boolean active) {
    }
}
