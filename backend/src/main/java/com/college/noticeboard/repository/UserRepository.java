package com.college.noticeboard.repository;

import com.college.noticeboard.entity.AccountStatus;
import com.college.noticeboard.entity.Role;
import com.college.noticeboard.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByResetTokenHash(String resetTokenHash);

    long countByRole(Role role);

    // "Department_Id" (with the underscore) makes Spring Data follow user.department.id.
    // Without it, User#getDepartmentId() is mistaken for a mapped field and startup fails.
    long countByDepartment_Id(Long departmentId);

    List<User> findByRoleAndActiveTrue(Role role);

    List<User> findByRoleAndDepartment_IdAndActiveTrue(Role role, Long departmentId);

    List<User> findByStatusOrderByCreatedAtAsc(AccountStatus status);

    /** Classes that exist in a department, from its students: [semester, className, count]. */
    @Query("select u.semester, u.className, count(u) from User u where u.role = com.college.noticeboard.entity.Role.STUDENT "
            + "and u.department.id = :departmentId and u.status in :statuses and u.semester is not null "
            + "and u.className is not null group by u.semester, u.className order by u.semester, u.className")
    List<Object[]> countStudentsByClass(@Param("departmentId") Long departmentId,
                                        @Param("statuses") Collection<AccountStatus> statuses);

    @Modifying
    @Query("update User u set u.status = case when u.active = true "
            + "then com.college.noticeboard.entity.AccountStatus.APPROVED "
            + "else com.college.noticeboard.entity.AccountStatus.DEACTIVATED end where u.status is null")
    int backfillStatus();
}
