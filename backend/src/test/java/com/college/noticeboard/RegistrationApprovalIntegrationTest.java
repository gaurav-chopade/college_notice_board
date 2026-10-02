package com.college.noticeboard;

import com.college.noticeboard.config.JwtService;
import com.college.noticeboard.config.UserPrincipal;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.repository.DepartmentRepository;
import com.college.noticeboard.repository.UserRepository;
import com.college.noticeboard.service.CloudStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Self-registration and the approval chain: HOD → Principal, teacher → HOD, student → class teacher. */
@SpringBootTest(properties = "app.auth.auto-activate-registrations=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationApprovalIntegrationTest {

    static final String PASSWORD = "Str0ng!pass";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;

    @MockBean CloudStorageService storage;

    Department cs, mech, civil;
    User admin, principal, hodCs, facCs, facCs2, facMech;

    @BeforeEach
    void setUp() {
        cs = departmentRepository.save(Department.builder().name("Computer Science").code("CS").build());
        mech = departmentRepository.save(Department.builder().name("Mechanical").code("MECH").build());
        civil = departmentRepository.save(Department.builder().name("Civil").code("CIVIL").build());
        admin = user("admin@t.edu", Role.SUPER_ADMIN, null);
        principal = user("principal@t.edu", Role.PRINCIPAL, null);
        hodCs = user("hod.cs@t.edu", Role.HOD, cs);
        facCs = user("fac.cs@t.edu", Role.FACULTY, cs);
        facCs2 = user("fac2.cs@t.edu", Role.FACULTY, cs);
        facMech = user("fac.mech@t.edu", Role.FACULTY, mech);
    }

    // ---------------------------------------------------------------- helpers

    private User user(String email, Role role, Department d) {
        return userRepository.save(User.builder().fullName(email).email(email).password(passwordEncoder.encode(PASSWORD))
                .role(role).department(d).active(true).build());
    }

    private String bearer(User u) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(u));
    }

    private ResultActions register(String email, Role role, Department d, Integer sem, String cls) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Applicant " + email);
        body.put("email", email);
        body.put("password", PASSWORD);
        body.put("role", role);
        body.put("departmentId", d.getId());
        body.put("semester", sem);
        body.put("className", cls);
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("email", email, "password", password))));
    }

    private long idOf(String email) {
        return userRepository.findByEmailIgnoreCase(email).orElseThrow().getId();
    }

    private Set<Long> queue(User reviewer) throws Exception {
        String json = mvc.perform(get("/api/registrations/pending").header("Authorization", bearer(reviewer)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<Long> ids = new HashSet<>();
        om.readTree(json).forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private ResultActions approve(User reviewer, long id) throws Exception {
        return mvc.perform(post("/api/registrations/" + id + "/approve").header("Authorization", bearer(reviewer)));
    }

    private ResultActions reject(User reviewer, long id, String reason) throws Exception {
        return mvc.perform(post("/api/registrations/" + id + "/reject").header("Authorization", bearer(reviewer))
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("reason", reason))));
    }

    private void assignClassTeacher(User by, Department d, int sem, String cls, User teacher) throws Exception {
        mvc.perform(put("/api/class-teachers").header("Authorization", bearer(by)).contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("departmentId", d.getId(), "semester", sem, "className", cls,
                                "teacherId", teacher.getId()))))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- students

    @Test
    void studentGoesToClassTeacherAndCannotSignInUntilApproved() throws Exception {
        assignClassTeacher(hodCs, cs, 6, "a", facCs);
        register("s1@t.edu", Role.STUDENT, cs, 6, "a")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.waitingFor").value("the class teacher of Computer Science semester 6 class A (fac.cs@t.edu)"));
        long id = idOf("s1@t.edu");

        // Wrong password reveals nothing; right password shows the status instead of a token.
        login("s1@t.edu", "Wr0ng!pass").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.account").doesNotExist());
        login("s1@t.edu", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.account.status").value("PENDING"))
                .andExpect(jsonPath("$.account.timeline[0].action").value("USER_REGISTERED"))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(queue(facCs)).containsExactly(id);
        assertThat(queue(facCs2)).isEmpty();
        assertThat(queue(hodCs)).contains(id);          // HOD oversees the whole department
        assertThat(queue(principal)).doesNotContain(id); // not escalated

        approve(facCs2, id).andExpect(status().isForbidden());   // another teacher of the same department
        approve(facMech, id).andExpect(status().isForbidden());
        approve(facCs, id).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        approve(facCs, id).andExpect(status().isBadRequest());   // already handled

        login("s1@t.edu", PASSWORD).andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
    }

    @Test
    void studentWithoutClassTeacherFallsBackToHodThenPrincipal() throws Exception {
        register("s2@t.edu", Role.STUDENT, cs, 4, "B")
                .andExpect(jsonPath("$.waitingFor").value("the Computer Science HOD (hod.cs@t.edu)"));
        long s2 = idOf("s2@t.edu");
        assertThat(queue(facCs)).isEmpty();
        approve(facCs, s2).andExpect(status().isForbidden());

        register("s3@t.edu", Role.STUDENT, civil, 2, "A")
                .andExpect(jsonPath("$.waitingFor").value("the Principal"));
        long s3 = idOf("s3@t.edu");
        assertThat(queue(principal)).contains(s3).doesNotContain(s2);
        approve(hodCs, s3).andExpect(status().isForbidden());   // another department's HOD
        approve(principal, s3).andExpect(status().isOk());
        approve(hodCs, s2).andExpect(status().isOk());
    }

    @Test
    void studentRegistrationNeedsSemesterAndClass() throws Exception {
        register("s4@t.edu", Role.STUDENT, cs, null, "A").andExpect(status().isBadRequest());
        register("s4@t.edu", Role.STUDENT, cs, 6, " ").andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- teachers and HODs

    @Test
    void teacherGoesToDepartmentHod() throws Exception {
        register("t1@t.edu", Role.FACULTY, cs, null, null)
                .andExpect(jsonPath("$.waitingFor").value("the Computer Science HOD (hod.cs@t.edu)"));
        long id = idOf("t1@t.edu");
        assertThat(queue(hodCs)).contains(id);
        approve(facCs, id).andExpect(status().isForbidden());   // teachers cannot approve teachers
        approve(hodCs, id).andExpect(status().isOk());
        assertThat(userRepository.findById(id).orElseThrow().getStatus()).isEqualTo(AccountStatus.APPROVED);
    }

    @Test
    void hodGoesToPrincipalAndOnlyOnePerDepartment() throws Exception {
        register("h1@t.edu", Role.HOD, cs, null, null).andExpect(status().isBadRequest());   // CS has an HOD

        register("h2@t.edu", Role.HOD, mech, null, null).andExpect(jsonPath("$.waitingFor").value("the Principal"));
        register("h3@t.edu", Role.HOD, mech, null, null).andExpect(status().isCreated());
        long h2 = idOf("h2@t.edu"), h3 = idOf("h3@t.edu");
        assertThat(queue(principal)).contains(h2, h3);
        approve(hodCs, h2).andExpect(status().isForbidden());   // HODs cannot approve HODs
        approve(principal, h2).andExpect(status().isOk());
        approve(principal, h3).andExpect(status().isBadRequest());   // Mechanical now has an HOD
    }

    @Test
    void studentsCannotReviewAndApplicantsCannotPickTheirRole() throws Exception {
        User student = userRepository.save(User.builder().fullName("s").email("s@t.edu").password(passwordEncoder.encode(PASSWORD))
                .role(Role.STUDENT).department(cs).semester(6).className("A").active(true).build());
        mvc.perform(get("/api/registrations/pending").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        register("p@t.edu", Role.PRINCIPAL, cs, null, null).andExpect(status().isBadRequest());
        register("a@t.edu", Role.SUPER_ADMIN, cs, null, null).andExpect(status().isBadRequest());
        mvc.perform(put("/api/class-teachers").header("Authorization", bearer(facCs)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":" + cs.getId() + ",\"semester\":6,\"className\":\"A\",\"teacherId\":" + facCs.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void hodManagesClassTeachersOfOwnDepartmentOnly() throws Exception {
        mvc.perform(put("/api/class-teachers").header("Authorization", bearer(hodCs)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":" + mech.getId() + ",\"semester\":6,\"className\":\"A\",\"teacherId\":" + facMech.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/class-teachers").header("Authorization", bearer(hodCs)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"departmentId\":" + cs.getId() + ",\"semester\":6,\"className\":\"A\",\"teacherId\":" + facMech.getId() + "}"))
                .andExpect(status().isBadRequest());   // teacher from another department
        assignClassTeacher(hodCs, cs, 6, "A", facCs);
        assignClassTeacher(hodCs, cs, 6, "A", facCs2);   // replaces, does not duplicate
        mvc.perform(get("/api/class-teachers").header("Authorization", bearer(hodCs)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].teacherName").value("fac2.cs@t.edu"));
    }

    // ---------------------------------------------------------------- rejection, suspension, rate limit

    @Test
    void rejectedApplicantSeesReasonAndCanApplyAgain() throws Exception {
        register("r1@t.edu", Role.FACULTY, cs, null, null);
        long id = idOf("r1@t.edu");
        reject(hodCs, id, "Use your college email address").andExpect(status().isOk());

        String json = login("r1@t.edu", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.account.status").value("REJECTED"))
                .andExpect(jsonPath("$.account.reason").value("Use your college email address"))
                .andExpect(jsonPath("$.account.reviewedByName").value("hod.cs@t.edu"))
                .andReturn().getResponse().getContentAsString();
        JsonNode timeline = om.readTree(json).get("account").get("timeline");
        assertThat(timeline.get(1).get("action").asText()).isEqualTo("USER_REJECTED");
        assertThat(timeline.get(1).get("note").asText()).isEqualTo("Use your college email address");

        register("r1@t.edu", Role.FACULTY, cs, null, null).andExpect(status().isCreated());
        assertThat(idOf("r1@t.edu")).isEqualTo(id);   // same record, back to pending
        assertThat(queue(hodCs)).contains(id);
        register("r1@t.edu", Role.FACULTY, cs, null, null).andExpect(status().isBadRequest());   // pending: not again
    }

    @Test
    void suspendedUserIsToldWhyAndLosesTokenAccess() throws Exception {
        String token = bearer(facCs);
        mvc.perform(put("/api/users/" + facCs.getId() + "/suspend").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Pending inquiry\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mvc.perform(get("/api/notices").header("Authorization", token)).andExpect(status().isUnauthorized());
        login("fac.cs@t.edu", PASSWORD).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.account.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.account.reason").value("Pending inquiry"));
    }

    @Test
    void repeatedWrongPasswordsAreRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            login("principal@t.edu", "Wr0ng!pass" + i).andExpect(status().isUnauthorized());
        }
        login("principal@t.edu", PASSWORD).andExpect(status().isTooManyRequests());
        // Other accounts are unaffected
        login("admin@t.edu", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void unknownEmailAndWrongPasswordLookTheSame() throws Exception {
        String a = login("nobody@t.edu", PASSWORD).andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String b = login("hod.cs@t.edu", "Wr0ng!pass").andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(om.readTree(a).get("message")).isEqualTo(om.readTree(b).get("message"));
    }
}
