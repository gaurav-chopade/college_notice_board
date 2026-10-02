package com.college.noticeboard.controller;

import com.college.noticeboard.dto.RegistrationDtos.*;
import com.college.noticeboard.service.ClassTeacherService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Class teachers")
@RestController
@RequestMapping("/api/class-teachers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','PRINCIPAL','HOD')")
public class ClassTeacherController {

    private final ClassTeacherService classTeacherService;

    @GetMapping
    public List<ClassTeacherResponse> list(@RequestParam(required = false) Long departmentId) {
        return classTeacherService.list(departmentId);
    }

    @GetMapping("/options")
    public ClassTeacherOptions options(@RequestParam(required = false) Long departmentId) {
        return classTeacherService.options(departmentId);
    }

    @PutMapping
    public ClassTeacherResponse assign(@Valid @RequestBody ClassTeacherRequest request) {
        return classTeacherService.assign(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable Long id) {
        classTeacherService.remove(id);
    }
}
