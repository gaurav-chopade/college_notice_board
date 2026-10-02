package com.college.noticeboard;

import com.college.noticeboard.config.JwtService;
import com.college.noticeboard.config.UserPrincipal;
import com.college.noticeboard.entity.*;
import com.college.noticeboard.repository.*;
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

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public site and the Engineering / Polytechnic split. No Authorization header means an anonymous visitor. */
@SpringBootTest(properties = "app.auth.auto-activate-registrations=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PublicNoticeIntegrationTest {

    static final String PASSWORD = "Str0ng!pass";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired UserRepository userRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired NoticeCategoryRepository categoryRepository;
    @Autowired NoticeRepository noticeRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired NotificationRepository notificationRepository;

    @MockBean CloudStorageService storage;

    Department engCo, polyCo;
    NoticeCategory admission, general;
    User admin, principal, hodEng, hodPoly, facEng, facPoly, studentEng;

    @BeforeEach
    void setUp() {
        engCo = departmentRepository.save(Department.builder().name("Computer Engineering").code("CO")
                .institutionType(InstitutionType.ENGINEERING).programName("B.Tech").semesterCount(8).build());
        polyCo = departmentRepository.save(Department.builder().name("Computer Engineering").code("DCO")
                .institutionType(InstitutionType.POLYTECHNIC).programName("Diploma").semesterCount(6).build());
        admission = categoryRepository.save(NoticeCategory.builder().name("Admission").build());
        general = categoryRepository.save(NoticeCategory.builder().name("General").build());
        admin = user("admin@t.edu", Role.SUPER_ADMIN, null, null, null);
        principal = user("principal@t.edu", Role.PRINCIPAL, null, null, null);
        hodEng = user("hod.eng@t.edu", Role.HOD, engCo, null, null);
        hodPoly = user("hod.poly@t.edu", Role.HOD, polyCo, null, null);
        facEng = user("fac.eng@t.edu", Role.FACULTY, engCo, null, null);
        facPoly = user("fac.poly@t.edu", Role.FACULTY, polyCo, null, null);
        studentEng = user("s.eng@t.edu", Role.STUDENT, engCo, 6, "A");
        when(storage.signedDownloadUrl(anyString(), anyString(), anyString(), anyLong())).thenReturn("https://signed.example/file");
    }

    // ---------------------------------------------------------------- helpers

    private User user(String email, Role role, Department d, Integer sem, String cls) {
        return userRepository.save(User.builder().fullName(email).email(email).password(passwordEncoder.encode(PASSWORD))
                .role(role).department(d).semester(sem).className(cls).active(true).build());
    }

    private String bearer(User u) {
        return "Bearer " + jwtService.generateToken(new UserPrincipal(u));
    }

    /** A notice written straight to the database in any state, to test what the public API lets through. */
    private Notice saved(String title, NoticeVisibility v, InstitutionScope scope, NoticeStatus status,
                         LocalDateTime publishDate, LocalDateTime expiry) {
        Notice n = Notice.builder().title(title).description("About " + title).category(admission).priority(Priority.NORMAL)
                .createdBy(principal).status(status).visibility(v).institutionScope(scope)
                .publishDate(publishDate).expiryDate(expiry).build();
        return noticeRepository.saveAndFlush(n);
    }

    private Notice live(String title, InstitutionScope scope) {
        return saved(title, NoticeVisibility.PUBLIC, scope, NoticeStatus.PUBLISHED, LocalDateTime.now().minusHours(1), null);
    }

    private List<String> publicTitles(String query) throws Exception {
        String json = mvc.perform(get("/api/public/notices" + query)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> titles = new ArrayList<>();
        om.readTree(json).get("content").forEach(n -> titles.add(n.get("title").asText()));
        return titles;
    }

    private ResultActions postNotice(User by, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Notice by " + by.getEmail());
        body.put("description", "Details");
        body.put("categoryId", admission.getId());
        body.put("priority", "NORMAL");
        body.putAll(extra);
        return mvc.perform(post("/api/notices").header("Authorization", bearer(by))
                .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)));
    }

    private Map<String, Object> publicBody(String scope) {
        Map<String, Object> m = new HashMap<>();
        m.put("visibility", "PUBLIC");
        m.put("institutionScope", scope);
        m.put("targets", List.of());
        return m;
    }

    private long idOf(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------------------------------------------------------------- what visitors can see

    @Test
    void visitorsSeeOnlyPublicPublishedLiveNotices() throws Exception {
        LocalDateTime now = LocalDateTime.now();
        Notice visible = live("Visible", InstitutionScope.BOTH);
        List<Notice> hidden = List.of(
                saved("Private", NoticeVisibility.PRIVATE, null, NoticeStatus.PUBLISHED, now.minusHours(1), null),
                saved("Draft", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.DRAFT, null, null),
                saved("Pending", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.PENDING_APPROVAL, null, null),
                saved("Rejected", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.REJECTED, null, null),
                saved("Scheduled", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.SCHEDULED, now.plusDays(1), null),
                saved("Expired", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.EXPIRED, now.minusDays(5), now.minusDays(1)),
                saved("Past expiry, not yet swept", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.PUBLISHED, now.minusDays(5), now.minusMinutes(1)),
                saved("Archived", NoticeVisibility.PUBLIC, InstitutionScope.BOTH, NoticeStatus.ARCHIVED, now.minusDays(5), null));

        assertThat(publicTitles("")).containsExactly("Visible");
        mvc.perform(get("/api/public/notices/" + visible.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Visible"));
        for (Notice n : hidden) {   // guessing ids gets nothing, and looks the same as a missing id
            mvc.perform(get("/api/public/notices/" + n.getId())).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/public/notices/999999")).andExpect(status().isNotFound());
    }

    @Test
    void publicDetailCarriesNoInternalFields() throws Exception {
        Notice n = live("Admission", InstitutionScope.ENGINEERING);
        mvc.perform(get("/api/public/notices/" + n.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedBy").value("Office of the Principal"))
                .andExpect(jsonPath("$.institutionScope").value("ENGINEERING"))
                .andExpect(jsonPath("$.createdById").doesNotExist())
                .andExpect(jsonPath("$.createdByName").doesNotExist())
                .andExpect(jsonPath("$.targets").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.rejectionReason").doesNotExist())
                .andExpect(jsonPath("$.permissions").doesNotExist());
    }

    @Test
    void institutionFilterSearchAndCategory() throws Exception {
        live("B.Tech admission", InstitutionScope.ENGINEERING);
        live("Diploma admission", InstitutionScope.POLYTECHNIC);
        Notice helpline = live("Admission helpline", InstitutionScope.BOTH);
        helpline.setCategory(general);
        noticeRepository.saveAndFlush(helpline);

        assertThat(publicTitles("?institution=ENGINEERING")).containsExactlyInAnyOrder("B.Tech admission", "Admission helpline");
        assertThat(publicTitles("?institution=POLYTECHNIC")).containsExactlyInAnyOrder("Diploma admission", "Admission helpline");
        assertThat(publicTitles("?q=diploma")).containsExactly("Diploma admission");
        assertThat(publicTitles("?categoryId=" + general.getId())).containsExactly("Admission helpline");
        assertThat(publicTitles("?institution=POLYTECHNIC&categoryId=" + admission.getId())).containsExactly("Diploma admission");
    }

    @Test
    void attachmentsOnlyThroughAPubliclyVisibleNotice() throws Exception {
        Notice pub = live("With form", InstitutionScope.BOTH);
        Notice priv = saved("Private with file", NoticeVisibility.PRIVATE, null, NoticeStatus.PUBLISHED, LocalDateTime.now().minusHours(1), null);
        Attachment a = attachment(pub);
        Attachment b = attachment(priv);

        mvc.perform(get("/api/public/notices/" + pub.getId())).andExpect(jsonPath("$.attachments[0].id").value(a.getId()));
        mvc.perform(get("/api/public/notices/" + pub.getId() + "/attachments/" + a.getId() + "/link"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("https://signed.example/file"));
        // A private attachment cannot be reached, either directly or by pairing it with a public notice id.
        mvc.perform(get("/api/public/notices/" + priv.getId() + "/attachments/" + b.getId() + "/link")).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/notices/" + pub.getId() + "/attachments/" + b.getId() + "/link")).andExpect(status().isNotFound());
        // The member endpoint still needs sign-in.
        mvc.perform(get("/api/attachments/" + a.getId() + "/download")).andExpect(status().isUnauthorized());
    }

    private Attachment attachment(Notice n) {
        return attachmentRepository.saveAndFlush(Attachment.builder().notice(n).originalFileName("form.pdf")
                .cloudinaryPublicId("x/" + UUID.randomUUID()).cloudinaryUrl("https://res.example/x").resourceType("raw")
                .fileFormat("pdf").fileSize(1000L).uploadedBy(principal).uploadedAt(LocalDateTime.now()).build());
    }

    @Test
    void privateEndpointsStillNeedSignIn() throws Exception {
        Notice n = live("Public", InstitutionScope.BOTH);
        mvc.perform(get("/api/notices/" + n.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notices")).andExpect(status().isUnauthorized());
        // Signed-in members can open public notices even when they are not targeted.
        mvc.perform(get("/api/notices/" + n.getId()).header("Authorization", bearer(studentEng))).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- who may publish publicly

    @Test
    void principalPublishesPublicNoticeWithoutNotifyingAnyone() throws Exception {
        long id = idOf(postNotice(principal, publicBody("BOTH")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.visibility").value("PUBLIC")));
        assertThat(publicTitles("")).isEmpty();   // a draft is not public yet
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(principal))).andExpect(status().isOk());
        assertThat(publicTitles("")).hasSize(1);
        assertThat(notificationRepository.countByNoticeId(id)).isZero();   // no targets: nobody notified, never everybody
    }

    @Test
    void hodsAndTeachersPublishOnlyForTheirOwnWing() throws Exception {
        postNotice(hodEng, publicBody("POLYTECHNIC")).andExpect(status().isForbidden());
        postNotice(hodEng, publicBody("BOTH")).andExpect(status().isForbidden());
        postNotice(hodPoly, publicBody("ENGINEERING")).andExpect(status().isForbidden());
        postNotice(facPoly, publicBody("ENGINEERING")).andExpect(status().isForbidden());
        long id = idOf(postNotice(hodEng, publicBody("ENGINEERING")).andExpect(status().isCreated()));
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(hodEng))).andExpect(status().isOk());
        assertThat(publicTitles("?institution=ENGINEERING")).hasSize(1);
        assertThat(publicTitles("?institution=POLYTECHNIC")).isEmpty();
    }

    @Test
    void teachersPublicNoticeGoesThroughHodApproval() throws Exception {
        long id = idOf(postNotice(facPoly, publicBody("POLYTECHNIC")).andExpect(status().isCreated()));
        mvc.perform(post("/api/notices/" + id + "/publish").header("Authorization", bearer(facPoly))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices/" + id + "/submit").header("Authorization", bearer(facPoly))).andExpect(status().isOk());
        mvc.perform(get("/api/public/notices/" + id)).andExpect(status().isNotFound());   // pending
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(hodEng))).andExpect(status().isForbidden());
        mvc.perform(post("/api/notices/" + id + "/approve").header("Authorization", bearer(hodPoly))).andExpect(status().isOk());
        mvc.perform(get("/api/public/notices/" + id)).andExpect(status().isOk());
    }

    @Test
    void visibilityRulesOnCreate() throws Exception {
        postNotice(studentEng, publicBody("BOTH")).andExpect(status().isForbidden());
        Map<String, Object> noScope = publicBody(null);
        postNotice(principal, noScope).andExpect(status().isBadRequest());
        postNotice(principal, Map.of("targets", List.of())).andExpect(status().isBadRequest());   // private needs targets
        postNotice(principal, Map.of()).andExpect(status().isBadRequest());                       // old clients: private, no targets
    }

    // ---------------------------------------------------------------- Engineering / Polytechnic separation

    @Test
    void approvalsStayInsideEachWing() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("role", "FACULTY", "fullName", "T", "email", "t.poly@t.edu",
                "password", PASSWORD, "departmentId", polyCo.getId(), "institutionType", "POLYTECHNIC"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isCreated());
        long teacher = userRepository.findByEmailIgnoreCase("t.poly@t.edu").orElseThrow().getId();
        mvc.perform(post("/api/registrations/" + teacher + "/approve").header("Authorization", bearer(hodEng))).andExpect(status().isForbidden());
        mvc.perform(post("/api/registrations/" + teacher + "/approve").header("Authorization", bearer(hodPoly))).andExpect(status().isOk());

        Map<String, Object> student = new HashMap<>(Map.of("role", "STUDENT", "fullName", "S", "email", "s.new@t.edu",
                "password", PASSWORD, "departmentId", engCo.getId(), "semester", 6, "className", "A"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(student)))
                .andExpect(status().isCreated());
        long s = userRepository.findByEmailIgnoreCase("s.new@t.edu").orElseThrow().getId();
        mvc.perform(post("/api/registrations/" + s + "/approve").header("Authorization", bearer(facPoly))).andExpect(status().isForbidden());
        mvc.perform(post("/api/registrations/" + s + "/approve").header("Authorization", bearer(hodPoly))).andExpect(status().isForbidden());
    }

    @Test
    void registrationChecksDepartmentBelongsToChosenWing() throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("role", "STUDENT", "fullName", "S", "email", "x@t.edu",
                "password", PASSWORD, "departmentId", polyCo.getId(), "institutionType", "ENGINEERING", "semester", 2, "className", "A"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.put("institutionType", "POLYTECHNIC");
        body.put("semester", 7);   // a diploma has six semesters
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void institutionsListBothWingsFromTheDatabase() throws Exception {
        String json = mvc.perform(get("/api/public/institutions")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode list = om.readTree(json);
        assertThat(list.get(0).get("type").asText()).isEqualTo("ENGINEERING");
        assertThat(list.get(0).get("programs").get(0).asText()).isEqualTo("B.Tech");
        assertThat(list.get(1).get("departments").get(0).get("semesterCount").asInt()).isEqualTo(6);
        // Same department name in both wings is allowed; same code is not.
        mvc.perform(post("/api/departments").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Civil Engineering\",\"code\":\"CE\",\"institutionType\":\"ENGINEERING\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/departments").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Civil Engineering\",\"code\":\"DCE\",\"institutionType\":\"POLYTECHNIC\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/departments").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Civil Engineering\",\"code\":\"CE2\",\"institutionType\":\"ENGINEERING\"}"))
                .andExpect(status().isBadRequest());
    }
}
