package com.college.noticeboard;

import com.college.noticeboard.service.CloudStorageService;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.repository.*;
import com.college.noticeboard.config.JwtService;
import com.college.noticeboard.config.UserPrincipal;
import com.college.noticeboard.service.NoticeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the critical acceptance criteria through the real HTTP layer,
 * Spring Security, JPA (H2) and the visibility rules. Cloudinary is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NoticeBoardIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired NoticeCategoryRepository categoryRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired NoticeService noticeService;

    @MockBean CloudStorageService storage;

    Department cs, mech;
    Long categoryId;
    User admin, principal, hodCs, hodMech, facCs, facMech, stuCs6A, stuCs6B, stuCs4A, stuMech6A;

    @BeforeEach
    void setUp() {
        cs = departmentRepository.save(Department.builder().name("Computer Science").code("CS").build());
        mech = departmentRepository.save(Department.builder().name("Mechanical").code("MECH").build());
        categoryId = categoryRepository.save(NoticeCategory.builder().name("General").build()).getId();
        categoryRepository.save(NoticeCategory.builder().name("Examination").build());
        admin = user("admin@t.edu", Role.SUPER_ADMIN, null, null, null);
        principal = user("principal@t.edu", Role.PRINCIPAL, null, null, null);
        hodCs = user("hod.cs@t.edu", Role.HOD, cs, null, null);
        hodMech = user("hod.mech@t.edu", Role.HOD, mech, null, null);
        facCs = user("fac.cs@t.edu", Role.FACULTY, cs, null, null);
        facMech = user("fac.mech@t.edu", Role.FACULTY, mech, null, null);
        stuCs6A = user("s.cs6a@t.edu", Role.STUDENT, cs, 6, "A");
        stuCs6B = user("s.cs6b@t.edu", Role.STUDENT, cs, 6, "B");
        stuCs4A = user("s.cs4a@t.edu", Role.STUDENT, cs, 4, "A");
        stuMech6A = user("s.mech6a@t.edu", Role.STUDENT, mech, 6, "A");
    }

    // ---------------------------------------------------------------- helpers

    private User user(String email, Role role, Department d, Integer sem, String cls) {
        return userRepository.save(User.builder().fullName(email).email(email)
                .password(passwordEncoder.encode("Password@123")).role(role).department(d)
                .semester(sem).className(cls).active(true).build());
    }

    private String bearer(User u) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(u));
    }

    private static Map<String, Object> target(TargetType type, Role role, Long dept, Integer sem, String cls) {
        Map<String, Object> m = new HashMap<>();
        m.put("targetType", type);
        m.put("targetRole", role);
        m.put("departmentId", dept);
        m.put("semester", sem);
        m.put("className", cls);
        return m;
    }

    private ResultActions postNotice(User by, String title, Priority p, LocalDateTime publishDate, Map<String, Object>... targets) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "Details of " + title);
        body.put("categoryId", categoryId);
        body.put("priority", p);
        body.put("publishDate", publishDate);
        body.put("expiryDate", LocalDateTime.now().plusDays(10));
        body.put("targets", List.of(targets));
        return mvc.perform(post("/api/notices").header("Authorization", bearer(by))
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    @SafeVarargs
    private long create(User by, String title, Map<String, Object>... targets) throws Exception {
        String json = postNotice(by, title, Priority.NORMAL, null, targets)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return om.readTree(json).get("id").asLong();
    }

    @SafeVarargs
    private long createAndPublish(User by, String title, Map<String, Object>... targets) throws Exception {
        long id = create(by, title, targets);
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(by)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        return id;
    }

    private Set<Long> feed(User u) throws Exception {
        String json = mvc.perform(get("/api/notices?scope=feed&size=100").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<Long> ids = new HashSet<>();
        om.readTree(json).get("content").forEach(n -> ids.add(n.get("id").asLong()));
        return ids;
    }

    private void assertSees(User u, long id, boolean expected) throws Exception {
        assertThat(feed(u).contains(id)).as(u.getEmail() + " feed contains " + id).isEqualTo(expected);
        mvc.perform(get("/api/notices/" + id).header("Authorization", bearer(u)))
                .andExpect(expected ? status().isOk() : status().isForbidden());
    }

    // ---------------------------------------------------------------- A, B: HOD-only notice

    @Test
    void principalHodOnlyNoticeVisibleOnlyToHods() throws Exception {
        long id = createAndPublish(principal, "HOD Meeting", target(TargetType.ROLE, Role.HOD, null, null, null));
        assertSees(hodCs, id, true);
        assertSees(hodMech, id, true);
        assertSees(facCs, id, false);
        assertSees(stuCs6A, id, false);
    }

    // ---------------------------------------------------------------- C, D, E, F

    @Test
    void facultyOnlyNoticeHiddenFromStudents() throws Exception {
        long id = createAndPublish(principal, "Faculty Meeting", target(TargetType.ROLE, Role.FACULTY, null, null, null));
        assertSees(facCs, id, true);
        assertSees(facMech, id, true);
        assertSees(stuCs6A, id, false);
        assertSees(hodCs, id, false);
    }

    @Test
    void hodPlusFacultyNotice() throws Exception {
        long id = createAndPublish(principal, "Joint meeting",
                target(TargetType.ROLE, Role.HOD, null, null, null), target(TargetType.ROLE, Role.FACULTY, null, null, null));
        assertSees(hodCs, id, true);
        assertSees(facMech, id, true);
        assertSees(stuMech6A, id, false);
    }

    @Test
    void collegeWideNoticeVisibleToAll() throws Exception {
        long id = createAndPublish(principal, "Holiday", target(TargetType.COLLEGE, null, null, null, null));
        for (User u : List.of(hodCs, facMech, stuCs6A, stuMech6A, admin)) {
            assertSees(u, id, true);
        }
    }

    // ---------------------------------------------------------------- G, H: HOD department targeting

    @Test
    void hodDepartmentNoticeVisibleOnlyInDepartment() throws Exception {
        long id = createAndPublish(hodCs, "CS students notice", target(TargetType.ROLE_DEPARTMENT, Role.STUDENT, cs.getId(), null, null));
        assertSees(stuCs6A, id, true);
        assertSees(stuCs4A, id, true);
        assertSees(stuMech6A, id, false);
        assertSees(facCs, id, false);
    }

    @Test
    void hodCanTargetSemesterAndClass() throws Exception {
        long sem = createAndPublish(hodCs, "Sem 6 exam", target(TargetType.SEMESTER, null, cs.getId(), 6, null));
        assertSees(stuCs6A, sem, true);
        assertSees(stuCs6B, sem, true);
        assertSees(stuCs4A, sem, false);
        assertSees(stuMech6A, sem, false);

        long cls = createAndPublish(hodCs, "6A lab", target(TargetType.CLASS, null, cs.getId(), 6, "a"));
        assertSees(stuCs6A, cls, true);
        assertSees(stuCs6B, cls, false);
    }

    @Test
    void hodCannotTargetOtherDepartmentOrCollege() throws Exception {
        postNotice(hodCs, "x", Priority.NORMAL, null, target(TargetType.ROLE_DEPARTMENT, Role.STUDENT, mech.getId(), null, null))
                .andExpect(status().isForbidden());
        postNotice(hodCs, "x", Priority.NORMAL, null, target(TargetType.COLLEGE, null, null, null, null))
                .andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- I: faculty approval workflow

    @Test
    void facultyNoticeRequiresHodApproval() throws Exception {
        long id = create(facCs, "Assignment", target(TargetType.CLASS, null, cs.getId(), 6, "A"));

        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(facCs))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facCs)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
        assertSees(stuCs6A, id, false);

        // Faculty, students and other departments' HODs cannot approve
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(facCs))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(hodMech))).andExpect(status().isBadRequest());

        // HOD of the department sees it in approvals and approves
        mvc.perform(get("/api/notices?scope=approvals").header("Authorization", bearer(hodCs)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(hodCs)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        assertSees(stuCs6A, id, true);
        assertSees(stuCs6B, id, false);
    }

    @Test
    void rejectionStoresReasonAndAllowsResubmission() throws Exception {
        long id = create(facCs, "Draft", target(TargetType.SEMESTER, null, cs.getId(), 6, null));
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facCs))).andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/reject").header("Authorization", bearer(hodCs))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Add the venue\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectionReason").value("Add the venue"));
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facCs)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void facultyCannotTargetFacultyOrOtherDepartment() throws Exception {
        postNotice(facCs, "x", Priority.NORMAL, null, target(TargetType.ROLE, Role.FACULTY, null, null, null)).andExpect(status().isForbidden());
        postNotice(facCs, "x", Priority.NORMAL, null, target(TargetType.SEMESTER, null, mech.getId(), 6, null)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- J: ID manipulation & role checks

    @Test
    void studentCannotCreateApproveOrPeekAtPrivateNotices() throws Exception {
        postNotice(stuCs6A, "x", Priority.NORMAL, null, target(TargetType.COLLEGE, null, null, null, null)).andExpect(status().isForbidden());
        long draft = create(principal, "Unpublished", target(TargetType.COLLEGE, null, null, null, null));
        mvc.perform(get("/api/notices/" + draft).header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notices/" + draft).header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notices/999999").header("Authorization", bearer(stuCs6A))).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notices").header("Authorization", "Bearer forged.token.value")).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String token = bearer(stuCs6A);
        stuCs6A.setActive(false);
        userRepository.saveAndFlush(stuCs6A);
        mvc.perform(get("/api/notices").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- K, L: notifications & statistics

    @Test
    void notificationsGoOnlyToIntendedRecipients() throws Exception {
        long id = createAndPublish(principal, "HOD only", target(TargetType.ROLE, Role.HOD, null, null, null));
        assertThat(notificationRepository.countByNoticeId(id)).isEqualTo(2);
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(hodCs.getId())).isEqualTo(1);
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(stuCs6A.getId())).isZero();
        assertThat(notificationRepository.countByUserIdAndIsReadFalse(facCs.getId())).isZero();
    }

    @Test
    void acknowledgementStatisticsCountOnlyRecipients() throws Exception {
        String json = postNotice(principal, "Exam fee", Priority.IMPORTANT, null, target(TargetType.SEMESTER, null, cs.getId(), 6, null))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = om.readTree(json).get("id").asLong();
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(principal))).andExpect(status().isOk());

        mvc.perform(post("/api/notices/" + id + "/acknowledge").header("Authorization", bearer(stuCs6A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acknowledged").value(true));
        mvc.perform(post("/api/notices/" + id + "/acknowledge").header("Authorization", bearer(stuMech6A)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/notices/" + id + "/stats").header("Authorization", bearer(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRecipients").value(2))
                .andExpect(jsonPath("$.read").value(1))
                .andExpect(jsonPath("$.acknowledged").value(1))
                .andExpect(jsonPath("$.notAcknowledged").value(1));
        mvc.perform(get("/api/notices/" + id + "/stats").header("Authorization", bearer(facCs))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- scheduling & expiry

    @Test
    void scheduledNoticePublishesAndExpires() throws Exception {
        String json = postNotice(principal, "Sports day", Priority.NORMAL, LocalDateTime.now().plusHours(1),
                target(TargetType.COLLEGE, null, null, null, null)).andReturn().getResponse().getContentAsString();
        long id = om.readTree(json).get("id").asLong();
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(principal)))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        assertSees(stuCs6A, id, false);

        noticeService.processDueNotices(LocalDateTime.now().plusHours(2));
        assertThat(noticeRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NoticeStatus.PUBLISHED);
        assertThat(notificationRepository.countByNoticeId(id)).isGreaterThan(0);

        noticeService.processDueNotices(LocalDateTime.now().plusDays(11));
        assertThat(noticeRepository.findById(id).orElseThrow().getStatus()).isEqualTo(NoticeStatus.EXPIRED);
        assertThat(feed(stuCs6A)).doesNotContain(id);
    }

    // ---------------------------------------------------------------- M, N, O: Cloudinary attachments

    @Test
    void attachmentsUploadToCloudAndStayPrivate() throws Exception {
        when(storage.upload(any(), anyString(), anyString(), anyBoolean())).thenAnswer(inv ->
                new CloudStorageService.UploadResult(inv.getArgument(1) + "/" + inv.getArgument(2),
                        "https://res.cloudinary.com/demo/raw/authenticated/x.pdf", "raw", "pdf", 12));
        when(storage.signedDownloadUrl(anyString(), anyString(), anyString(), anyLong())).thenReturn("https://signed.example/x");

        long id = createAndPublish(principal, "HOD circular", target(TargetType.ROLE, Role.HOD, null, null, null));
        MockMultipartFile pdf = new MockMultipartFile("files", "circular.pdf", "application/pdf", "%PDF-1.4 hello".getBytes());
        String json = mvc.perform(multipart("/api/notices/" + id + "/attachments").file(pdf).header("Authorization", bearer(principal)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].originalFileName").value("circular.pdf"))
                .andExpect(jsonPath("$[0].cloudinaryUrl").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long attachmentId = om.readTree(json).get(0).get("id").asLong();
        verify(storage).upload(any(), eq("college-notice-board/notices/" + id), anyString(), eq(false));

        mvc.perform(get("/api/notices/" + id + "/attachments").header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/attachments/" + attachmentId + "/download").header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/attachments/" + attachmentId + "/download").header("Authorization", bearer(hodCs)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://signed.example/x"));

        MockMultipartFile fake = new MockMultipartFile("files", "evil.pdf", "application/pdf", "MZ-not-a-pdf".getBytes());
        mvc.perform(multipart("/api/notices/" + id + "/attachments").file(fake).header("Authorization", bearer(principal)))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/notices/" + id).header("Authorization", bearer(principal))).andExpect(status().isNoContent());
        verify(storage).delete(startsWith("college-notice-board/notices/" + id), eq("raw"));
        assertThat(noticeRepository.findById(id)).isEmpty();
    }

    // ---------------------------------------------------------------- pagination, AI, auth

    @Test
    void paginationReturnsPageMetadata() throws Exception {
        for (int i = 0; i < 3; i++) {
            createAndPublish(principal, "N" + i, target(TargetType.COLLEGE, null, null, null, null));
        }
        mvc.perform(get("/api/notices?page=0&size=2").header("Authorization", bearer(stuCs6A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageNumber").value(0))
                .andExpect(jsonPath("$.pageSize").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void aiGeneratedNoticeIsAlwaysADraft() throws Exception {
        mvc.perform(post("/api/ai/notice-draft").header("Authorization", bearer(hodCs)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"College will remain closed tomorrow because of a public holiday.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MOCK"))
                .andExpect(jsonPath("$.draft.status").value("DRAFT"))
                .andExpect(jsonPath("$.draft.aiGenerated").value(true))
                .andExpect(jsonPath("$.draft.targets[0].targetType").value("DEPARTMENT"));
        mvc.perform(post("/api/ai/notice-draft").header("Authorization", bearer(stuCs6A)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Some text here\"}")).andExpect(status().isForbidden());
    }

    @Test
    void loginIssuesTokenAndRejectsBadPassword() throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"PRINCIPAL@t.edu\",\"password\":\"Password@123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("PRINCIPAL"))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = om.readTree(json);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + node.get("token").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("principal@t.edu"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"principal@t.edu\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void registrationCreatesStudentAndRejectsWeakPassword() throws Exception {
        String ok = "{\"fullName\":\"New\",\"email\":\"new@t.edu\",\"password\":\"Str0ng!pass\",\"departmentId\":" + cs.getId()
                + ",\"semester\":2,\"className\":\"b\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isCreated());
        User created = userRepository.findByEmailIgnoreCase("new@t.edu").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.STUDENT);
        assertThat(created.getPassword()).startsWith("$2");
        assertThat(created.getClassName()).isEqualTo("B");
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(ok.replace("Str0ng!pass", "weak")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminManagesUsersAndSeesAuditLogs() throws Exception {
        mvc.perform(get("/api/users").header("Authorization", bearer(principal))).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/audit-logs").header("Authorization", bearer(hodCs))).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit-logs").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard/admin").header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/student").header("Authorization", bearer(stuCs6A))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- live recipient preview

    @SafeVarargs
    private ResultActions preview(User by, Map<String, Object>... targets) throws Exception {
        return mvc.perform(post("/api/notices/recipient-preview").header("Authorization", bearer(by))
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(Map.of("targets", List.of(targets)))));
    }

    @Test
    void recipientPreviewCountsOnlyMatchingActiveUsers() throws Exception {
        preview(hodCs, target(TargetType.SEMESTER, null, cs.getId(), 6, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.byRole.STUDENT").value(2))
                .andExpect(jsonPath("$.audience[0]").value("Computer Science semester 6 students"));

        // Two overlapping audiences are not double-counted; inactive users are excluded.
        stuCs6B.setActive(false);
        userRepository.save(stuCs6B);
        preview(principal, target(TargetType.ROLE, Role.STUDENT, null, null, null),
                target(TargetType.DEPARTMENT, null, cs.getId(), null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))   // 3 active students + CS HOD + CS faculty
                .andExpect(jsonPath("$.byRole.STUDENT").value(3));

        // The creator is never counted as a recipient of their own notice.
        preview(principal, target(TargetType.COLLEGE, null, null, null, null))
                .andExpect(jsonPath("$.total").value(8));   // 10 users − principal − inactive student
    }

    @Test
    void recipientPreviewAppliesTheSameRulesAsSaving() throws Exception {
        preview(facCs, target(TargetType.ROLE, Role.FACULTY, null, null, null)).andExpect(status().isForbidden());
        preview(hodCs, target(TargetType.ROLE_DEPARTMENT, Role.STUDENT, mech.getId(), null, null)).andExpect(status().isForbidden());
        preview(hodCs, target(TargetType.SEMESTER, null, cs.getId(), null, null)).andExpect(status().isBadRequest());
        preview(stuCs6A, target(TargetType.COLLEGE, null, null, null, null)).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- approval timeline

    @Test
    void timelineShowsTheApprovalFlowToManagersOnly() throws Exception {
        long id = create(facCs, "Lab schedule", target(TargetType.CLASS, null, cs.getId(), 6, "A"));
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facCs))).andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/reject").header("Authorization", bearer(hodCs))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Add the venue\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facCs))).andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(hodCs))).andExpect(status().isOk());

        String json = mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(facCs)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> actions = new ArrayList<>();
        om.readTree(json).forEach(e -> actions.add(e.get("action").asText()));
        assertThat(actions).containsExactly("NOTICE_CREATED", "NOTICE_SUBMITTED", "NOTICE_REJECTED",
                "NOTICE_SUBMITTED", "NOTICE_APPROVED", "NOTICE_PUBLISHED");
        JsonNode rejected = om.readTree(json).get(2);
        assertThat(rejected.get("note").asText()).isEqualTo("Add the venue");
        assertThat(rejected.get("actorRole").asText()).isEqualTo("HOD");

        mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(hodCs))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(stuCs6A))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(facMech))).andExpect(status().isForbidden());
        mvc.perform(get("/api/notices/" + id + "/timeline").header("Authorization", bearer(hodMech))).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- saved notices

    private Set<Long> saved(User u) throws Exception {
        String json = mvc.perform(get("/api/notices?scope=saved&size=100").header("Authorization", bearer(u)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Set<Long> ids = new HashSet<>();
        om.readTree(json).get("content").forEach(n -> {
            assertThat(n.get("saved").asBoolean()).isTrue();
            ids.add(n.get("id").asLong());
        });
        return ids;
    }

    @Test
    void recipientsCanSaveAndUnsaveNotices() throws Exception {
        long id = createAndPublish(hodCs, "Exam timetable", target(TargetType.SEMESTER, null, cs.getId(), 6, null));

        mvc.perform(put("/api/notices/" + id + "/save").header("Authorization", bearer(stuCs6A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));
        // Saving twice is harmless
        mvc.perform(put("/api/notices/" + id + "/save").header("Authorization", bearer(stuCs6A))).andExpect(status().isOk());
        assertThat(saved(stuCs6A)).containsExactly(id);
        assertThat(saved(stuCs6B)).isEmpty();   // per user

        mvc.perform(get("/api/notices?scope=feed").header("Authorization", bearer(stuCs6A)))
                .andExpect(jsonPath("$.content[0].saved").value(true));

        mvc.perform(delete("/api/notices/" + id + "/save").header("Authorization", bearer(stuCs6A))).andExpect(status().isNoContent());
        assertThat(saved(stuCs6A)).isEmpty();
    }

    @Test
    void savingDoesNotBypassVisibility() throws Exception {
        long id = createAndPublish(hodCs, "Sem 6 only", target(TargetType.SEMESTER, null, cs.getId(), 6, null));
        mvc.perform(put("/api/notices/" + id + "/save").header("Authorization", bearer(stuCs4A))).andExpect(status().isForbidden());

        // A saved notice leaves the list when it is archived, and its saved rows go when it is deleted.
        mvc.perform(put("/api/notices/" + id + "/save").header("Authorization", bearer(stuCs6A))).andExpect(status().isOk());
        mvc.perform(post("/api/notices/" + id + "/archive").header("Authorization", bearer(hodCs))).andExpect(status().isOk());
        assertThat(saved(stuCs6A)).isEmpty();
        mvc.perform(delete("/api/notices/" + id).header("Authorization", bearer(hodCs))).andExpect(status().isNoContent());
    }

    // ---------------------------------------------------------------- notification filter and delete

    @Test
    void notificationsCanBeFilteredAndDeletedByTheirOwnerOnly() throws Exception {
        createAndPublish(principal, "One", target(TargetType.ROLE, Role.STUDENT, null, null, null));
        createAndPublish(principal, "Two", target(TargetType.ROLE, Role.STUDENT, null, null, null));

        String json = mvc.perform(get("/api/notifications").header("Authorization", bearer(stuCs6A)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].noticePriority").value("NORMAL"))
                .andReturn().getResponse().getContentAsString();
        long first = om.readTree(json).get("content").get(0).get("id").asLong();
        long second = om.readTree(json).get("content").get(1).get("id").asLong();

        mvc.perform(put("/api/notifications/" + first + "/read").header("Authorization", bearer(stuCs6A))).andExpect(status().isOk());
        mvc.perform(get("/api/notifications?unreadOnly=true").header("Authorization", bearer(stuCs6A)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(second));

        mvc.perform(delete("/api/notifications/" + second).header("Authorization", bearer(stuCs6B))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/notifications/" + second).header("Authorization", bearer(stuCs6A))).andExpect(status().isNoContent());
        mvc.perform(get("/api/notifications").header("Authorization", bearer(stuCs6A)))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
