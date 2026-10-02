package com.college.noticeboard;

import com.college.noticeboard.entity.*;
import com.college.noticeboard.service.TargetMatcher;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetMatcherTest {

    private final Department cs = Department.builder().id(1L).name("CS").code("CS").build();
    private final Department mech = Department.builder().id(2L).name("Mech").code("MECH").build();

    private User user(long id, Role role, Department d, Integer sem, String cls) {
        return User.builder().id(id).role(role).department(d).semester(sem).className(cls).build();
    }

    @Test
    void hodOnlyTargetMatchesOnlyHods() {
        NoticeTarget t = NoticeTarget.builder().targetType(TargetType.ROLE).targetRole(Role.HOD).build();
        assertThat(TargetMatcher.matches(t, user(1, Role.HOD, cs, null, null))).isTrue();
        assertThat(TargetMatcher.matches(t, user(2, Role.FACULTY, cs, null, null))).isFalse();
        assertThat(TargetMatcher.matches(t, user(3, Role.STUDENT, cs, 6, "A"))).isFalse();
    }

    @Test
    void collegeTargetMatchesEveryone() {
        NoticeTarget t = NoticeTarget.builder().targetType(TargetType.COLLEGE).build();
        assertThat(TargetMatcher.matches(t, user(1, Role.STUDENT, mech, 2, "B"))).isTrue();
        assertThat(TargetMatcher.matches(t, user(2, Role.PRINCIPAL, null, null, null))).isTrue();
    }

    @Test
    void classTargetMatchesOnlyThatClass() {
        NoticeTarget t = NoticeTarget.builder().targetType(TargetType.CLASS).targetRole(Role.STUDENT)
                .department(cs).semester(6).className("A").build();
        assertThat(TargetMatcher.matches(t, user(1, Role.STUDENT, cs, 6, "a"))).isTrue();
        assertThat(TargetMatcher.matches(t, user(2, Role.STUDENT, cs, 6, "B"))).isFalse();
        assertThat(TargetMatcher.matches(t, user(3, Role.STUDENT, cs, 4, "A"))).isFalse();
        assertThat(TargetMatcher.matches(t, user(4, Role.STUDENT, mech, 6, "A"))).isFalse();
    }

    @Test
    void departmentTargetExcludesUsersWithoutDepartment() {
        NoticeTarget t = NoticeTarget.builder().targetType(TargetType.DEPARTMENT).department(cs).build();
        assertThat(TargetMatcher.matches(t, user(1, Role.PRINCIPAL, null, null, null))).isFalse();
        assertThat(TargetMatcher.matches(t, user(2, Role.FACULTY, cs, null, null))).isTrue();
    }

    @Test
    void specificUserTargetMatchesOnlyThatUser() {
        User target = user(7, Role.STUDENT, cs, 6, "A");
        NoticeTarget t = NoticeTarget.builder().targetType(TargetType.SPECIFIC_USER).user(target).build();
        assertThat(TargetMatcher.matches(t, target)).isTrue();
        assertThat(TargetMatcher.matches(t, user(8, Role.STUDENT, cs, 6, "A"))).isFalse();
    }
}
