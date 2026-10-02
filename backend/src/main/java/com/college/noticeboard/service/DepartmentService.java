package com.college.noticeboard.service;

import com.college.noticeboard.dto.DepartmentDtos.DepartmentRequest;
import com.college.noticeboard.dto.DepartmentDtos.DepartmentResponse;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.Department;
import com.college.noticeboard.entity.InstitutionType;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.SimpleMappers;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.NoticeRepository;
import com.college.noticeboard.repository.NoticeTargetRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.config.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final NoticeRepository noticeRepository;
    private final NoticeTargetRepository noticeTargetRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<DepartmentResponse> list() {
        return departmentRepository.findAll(Sort.by("name")).stream().map(SimpleMappers::toResponse).toList();
    }

    @Transactional
    public DepartmentResponse create(DepartmentRequest req) {
        InstitutionType type = req.institutionType() == null ? InstitutionType.ENGINEERING : req.institutionType();
        if (departmentRepository.existsByNameIgnoreCaseAndInstitutionType(req.name().trim(), type)
                || departmentRepository.existsByCodeIgnoreCase(req.code().trim())) {
            throw new BadRequestException("A department with this name (in " + label(type) + ") or this code already exists");
        }
        Department d = departmentRepository.save(Department.builder().name(req.name().trim())
                .code(req.code().trim().toUpperCase(Locale.ROOT)).description(req.description())
                .institutionType(type).programName(blankToNull(req.programName())).semesterCount(req.semesterCount()).build());
        auditService.log(currentUserService.currentUser(), AuditAction.DEPARTMENT_CHANGED, "Department", d.getId(), "Created " + d.getName());
        return SimpleMappers.toResponse(d);
    }

    @Transactional
    public DepartmentResponse update(Long id, DepartmentRequest req) {
        Department d = departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        String name = req.name().trim();
        String code = req.code().trim().toUpperCase(Locale.ROOT);
        InstitutionType type = req.institutionType() == null ? d.getInstitutionType() : req.institutionType();
        boolean nameTaken = (!name.equalsIgnoreCase(d.getName()) || type != d.getInstitutionType())
                && departmentRepository.existsByNameIgnoreCaseAndInstitutionType(name, type);
        if (nameTaken || (!code.equalsIgnoreCase(d.getCode()) && departmentRepository.existsByCodeIgnoreCase(code))) {
            throw new BadRequestException("A department with this name (in " + label(type) + ") or this code already exists");
        }
        if (type != d.getInstitutionType() && userRepository.countByDepartment_Id(id) > 0) {
            throw new BadRequestException("Move this department's users first: its people belong to " + label(d.getInstitutionType()));
        }
        d.setName(name);
        d.setCode(code);
        d.setDescription(req.description());
        d.setInstitutionType(type);
        d.setProgramName(blankToNull(req.programName()));
        d.setSemesterCount(req.semesterCount());
        auditService.log(currentUserService.currentUser(), AuditAction.DEPARTMENT_CHANGED, "Department", id, "Updated " + name);
        return SimpleMappers.toResponse(d);
    }

    private static String label(InstitutionType t) {
        return t == InstitutionType.POLYTECHNIC ? "Polytechnic" : "Engineering";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional
    public void delete(Long id) {
        Department d = departmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Department not found"));
        if (userRepository.countByDepartment_Id(id) > 0 || noticeRepository.existsByDepartmentId(id)
                || noticeTargetRepository.existsByDepartmentId(id)) {
            throw new BadRequestException("This department still has users or notices. Move or remove them first");
        }
        departmentRepository.delete(d);
        auditService.log(currentUserService.currentUser(), AuditAction.DEPARTMENT_CHANGED, "Department", id, "Deleted " + d.getName());
    }
}
