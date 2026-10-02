package com.college.noticeboard.service;

import com.college.noticeboard.dto.CategoryDtos.CategoryRequest;
import com.college.noticeboard.dto.CategoryDtos.CategoryResponse;
import com.college.noticeboard.entity.AuditAction;
import com.college.noticeboard.entity.NoticeCategory;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ResourceNotFoundException;
import com.college.noticeboard.dto.SimpleMappers;
import com.college.noticeboard.repository.NoticeCategoryRepository;
import com.college.noticeboard.repository.NoticeRepository;
import com.college.noticeboard.config.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final NoticeCategoryRepository categoryRepository;
    private final NoticeRepository noticeRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(boolean includeInactive) {
        return (includeInactive ? categoryRepository.findAllByOrderByNameAsc() : categoryRepository.findByActiveTrueOrderByNameAsc())
                .stream().map(SimpleMappers::toResponse).toList();
    }

    @Transactional
    public CategoryResponse create(CategoryRequest req) {
        if (categoryRepository.existsByNameIgnoreCase(req.name().trim())) {
            throw new BadRequestException("A category with this name already exists");
        }
        NoticeCategory c = categoryRepository.save(NoticeCategory.builder().name(req.name().trim())
                .description(req.description()).active(req.active() == null || req.active()).build());
        auditService.log(currentUserService.currentUser(), AuditAction.CATEGORY_CHANGED, "Category", c.getId(), "Created " + c.getName());
        return SimpleMappers.toResponse(c);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest req) {
        NoticeCategory c = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        String name = req.name().trim();
        if (!name.equalsIgnoreCase(c.getName()) && categoryRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("A category with this name already exists");
        }
        c.setName(name);
        c.setDescription(req.description());
        if (req.active() != null) {
            c.setActive(req.active());
        }
        auditService.log(currentUserService.currentUser(), AuditAction.CATEGORY_CHANGED, "Category", id, "Updated " + name);
        return SimpleMappers.toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        NoticeCategory c = categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found"));
        if (noticeRepository.existsByCategoryId(id)) {
            throw new BadRequestException("Notices use this category. Deactivate it instead of deleting");
        }
        categoryRepository.delete(c);
        auditService.log(currentUserService.currentUser(), AuditAction.CATEGORY_CHANGED, "Category", id, "Deleted " + c.getName());
    }
}
