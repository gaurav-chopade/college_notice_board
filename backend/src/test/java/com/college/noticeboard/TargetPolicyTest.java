package com.college.noticeboard;

import com.college.noticeboard.dto.NoticeDtos.TargetRequest;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.exception.BadRequestException;
import com.college.noticeboard.exception.ForbiddenException;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.service.TargetPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TargetPolicyTest {

    @Mock
    DepartmentRepository departmentRepository;
    @Mock
    UserRepository userRepository;

    TargetPolicy policy;
    final Department cs = Department.builder().id(1L).name("CS").code("CS").build();
    final Department mech = Department.builder().id(2L).name("Mech").code("MECH").build();

    @BeforeEach
    void setUp() {
        policy = new TargetPolicy(departmentRepository, userRepository);
        when(departmentRepository.findById(1L)).thenReturn(Optional.of(cs));
        when(departmentRepository.findById(2L)).thenReturn(Optional.of(mech));
    }

    private User u(Role role, Department d) {
        return User.builder().id(99L).role(role).department(d).active(true).build();
    }

    @Test
    void principalCanTargetHodsAndCollege() {
        var targets = policy.buildTargets(u(Role.PRINCIPAL, null), List.of(
                new TargetRequest(TargetType.ROLE, Role.HOD, null, null, null, null),
                new TargetRequest(TargetType.COLLEGE, null, null, null, null, null)));
        assertThat(targets).hasSize(2);
    }

    @Test
    void hodCannotTargetCollegeOrOtherDepartment() {
        User hod = u(Role.HOD, cs);
        assertThatThrownBy(() -> policy.buildTargets(hod, List.of(new TargetRequest(TargetType.COLLEGE, null, null, null, null, null))))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.buildTargets(hod, List.of(new TargetRequest(TargetType.ROLE_DEPARTMENT, Role.STUDENT, 2L, null, null, null))))
                .isInstanceOf(ForbiddenException.class);
        assertThat(policy.buildTargets(hod, List.of(new TargetRequest(TargetType.SEMESTER, null, 1L, 6, null, null))))
                .singleElement().satisfies(t -> assertThat(t.getTargetRole()).isEqualTo(Role.STUDENT));
    }

    @Test
    void facultyCanOnlyTargetOwnStudents() {
        User fac = u(Role.FACULTY, cs);
        assertThat(policy.buildTargets(fac, List.of(new TargetRequest(TargetType.CLASS, null, 1L, 6, " a ", null))))
                .singleElement().satisfies(t -> assertThat(t.getClassName()).isEqualTo("A"));
        assertThatThrownBy(() -> policy.buildTargets(fac, List.of(new TargetRequest(TargetType.ROLE_DEPARTMENT, Role.FACULTY, 1L, null, null, null))))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.buildTargets(fac, List.of(new TargetRequest(TargetType.ROLE, Role.STUDENT, null, null, null, null))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void studentCannotTarget() {
        assertThatThrownBy(() -> policy.buildTargets(u(Role.STUDENT, cs), List.of(new TargetRequest(TargetType.COLLEGE, null, null, null, null, null))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void missingRequiredFieldsAreRejected() {
        assertThatThrownBy(() -> policy.buildTargets(u(Role.PRINCIPAL, null), List.of(new TargetRequest(TargetType.CLASS, null, 1L, 6, null, null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> policy.buildTargets(u(Role.PRINCIPAL, null), List.of(new TargetRequest(TargetType.ROLE, null, null, null, null, null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void duplicatesAreCollapsed() {
        var t = new TargetRequest(TargetType.ROLE, Role.FACULTY, null, null, null, null);
        assertThat(policy.buildTargets(u(Role.PRINCIPAL, null), List.of(t, t))).hasSize(1);
    }
}
