package com.college.noticeboard.config;

import com.college.noticeboard.entity.*;
import com.college.noticeboard.service.NotificationService;
import com.college.noticeboard.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Development sample data. Runs only when the users table is empty and app.seed.enabled=true. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "Password@123";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final NoticeCategoryRepository categoryRepository;
    private final NoticeRepository noticeRepository;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;
    private final ClassTeacherRepository classTeacherRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }
        log.info("Seeding development data...");

        Map<String, Department> d = new LinkedHashMap<>();
        for (String[] row : new String[][]{{"Computer Engineering", "CS"}, {"Information Technology", "IT"},
                {"Mechanical Engineering", "MECH"}, {"Civil Engineering", "CIVIL"}, {"Electronics Engineering", "ENTC"}}) {
            d.put(row[1], departmentRepository.save(Department.builder().name(row[0]).code(row[1])
                    .institutionType(InstitutionType.ENGINEERING).programName("B.Tech").semesterCount(8).build()));
        }
        // Polytechnic: same college, its own departments (names may repeat, codes may not).
        for (String[] row : new String[][]{{"Computer Engineering", "DCO"}, {"Mechanical Engineering", "DME"},
                {"Civil Engineering", "DCE"}, {"Electrical Engineering", "DEE"}}) {
            d.put(row[1], departmentRepository.save(Department.builder().name(row[0]).code(row[1])
                    .institutionType(InstitutionType.POLYTECHNIC).programName("Diploma").semesterCount(6).build()));
        }

        Map<String, NoticeCategory> c = new LinkedHashMap<>();
        for (String name : List.of("Academic", "Examination", "Placement", "Event", "Holiday", "Scholarship", "Admission",
                "Sports", "Cultural", "Workshop", "Seminar", "Fee", "Library", "General",
                "Courses", "Entrance Examination", "Important Dates")) {
            c.put(name, categoryRepository.save(NoticeCategory.builder().name(name).build()));
        }

        User admin = user("Super Admin", "admin@college.edu", Role.SUPER_ADMIN, null, null, null);
        User principal = user("Dr. Meera Kulkarni", "principal@college.edu", Role.PRINCIPAL, null, null, null);
        User hodCs = user("Dr. Arvind Rao", "hod.cs@college.edu", Role.HOD, d.get("CS"), null, null);
        User hodMech = user("Dr. Sunita Patil", "hod.mech@college.edu", Role.HOD, d.get("MECH"), null, null);
        User facCs = user("Prof. Rahul Deshmukh", "faculty.cs@college.edu", Role.FACULTY, d.get("CS"), null, null);
        user("Prof. Kavita Joshi", "faculty.mech@college.edu", Role.FACULTY, d.get("MECH"), null, null);
        User stuCs6a = user("Aarav Shah", "student.cs6a@college.edu", Role.STUDENT, d.get("CS"), 6, "A");

        // Prof. Deshmukh is class teacher of CS 6A, so new 6A students go to him for approval.
        classTeacherRepository.save(ClassTeacher.builder().department(d.get("CS")).semester(6).className("A")
                .teacher(facCs).assignedAt(LocalDateTime.now()).build());
        // Two requests waiting, so the approval queues have something to show.
        pending("Rohan Kale", "rohan.kale@college.edu", Role.STUDENT, d.get("CS"), 6, "A");
        pending("Prof. Neha Gupta", "neha.gupta@college.edu", Role.FACULTY, d.get("CS"), null, null);
        user("Diya Nair", "student.cs6b@college.edu", Role.STUDENT, d.get("CS"), 6, "B");
        user("Kabir Mehta", "student.cs4a@college.edu", Role.STUDENT, d.get("CS"), 4, "A");
        user("Ishaan Kulkarni", "student.mech6a@college.edu", Role.STUDENT, d.get("MECH"), 6, "A");
        user("Ananya Iyer", "student.it6a@college.edu", Role.STUDENT, d.get("IT"), 6, "A");
        // Polytechnic computer department
        User hodDco = user("Prof. Sameer Pawar", "hod.dco@college.edu", Role.HOD, d.get("DCO"), null, null);
        User facDco = user("Prof. Pooja Shinde", "faculty.dco@college.edu", Role.FACULTY, d.get("DCO"), null, null);
        user("Om Jadhav", "student.dco5a@college.edu", Role.STUDENT, d.get("DCO"), 5, "A");
        classTeacherRepository.save(ClassTeacher.builder().department(d.get("DCO")).semester(5).className("A")
                .teacher(facDco).assignedAt(LocalDateTime.now()).build());

        LocalDateTime now = LocalDateTime.now();

        // Public notices: readable on the public site without signing in.
        publicNotice("B.Tech Admission 2026-27",
                "Applications are open for first-year B.Tech admission in Computer, IT, Mechanical, Civil and Electronics "
                        + "Engineering. Register on the state CAP portal and choose this college in your option form. "
                        + "Visit the admission office for help with document verification.",
                c.get("Admission"), Priority.IMPORTANT, principal, now.plusDays(60), InstitutionScope.ENGINEERING);
        publicNotice("Engineering CAP round schedule",
                "CAP round 1 allotment will be displayed next week. Candidates allotted to this college must report "
                        + "with original documents within three days of allotment.",
                c.get("Important Dates"), Priority.NORMAL, principal, now.plusDays(30), InstitutionScope.ENGINEERING);
        publicNotice("Diploma Admission 2026-27",
                "Admissions are open for three-year diploma programmes in Computer, Mechanical, Civil and Electrical "
                        + "Engineering. Students who have passed SSC can apply through the state diploma admission portal.",
                c.get("Admission"), Priority.IMPORTANT, principal, now.plusDays(60), InstitutionScope.POLYTECHNIC);
        publicNotice("Diploma courses available",
                "The polytechnic offers diploma programmes in Computer, Mechanical, Civil and Electrical Engineering, "
                        + "each six semesters long, with industry training in the final year.",
                c.get("Courses"), Priority.NORMAL, hodDco, now.plusDays(90), InstitutionScope.POLYTECHNIC);
        publicNotice("Admission helpline",
                "For help with Engineering or Polytechnic admission, call the admission office or visit between "
                        + "10:00 am and 5:00 pm, Monday to Saturday.",
                c.get("General"), Priority.NORMAL, principal, now.plusDays(90), InstitutionScope.BOTH);
        publicNotice("Scholarship information for new applicants",
                "Students admitted this year can apply for government scholarships through the state scholarship portal. "
                        + "The scholarship cell will guide applicants after admission.",
                c.get("Scholarship"), Priority.NORMAL, principal, now.plusDays(45), InstitutionScope.BOTH);

        publish(notice("College closed for public holiday",
                "The college will remain closed tomorrow on account of the public holiday. Regular classes resume the day after.",
                c.get("Holiday"), Priority.IMPORTANT, principal, now.plusDays(3)), target(TargetType.COLLEGE, null, null, null, null));

        publish(notice("HOD meeting in the Principal's office",
                "All Heads of Department are requested to attend a meeting on Friday at 11:00 AM to review the academic calendar.",
                c.get("General"), Priority.URGENT, principal, now.plusDays(5)), target(TargetType.ROLE, Role.HOD, null, null, null));

        publish(notice("Faculty meeting on NAAC preparation",
                "All faculty members must attend the NAAC preparation meeting in the seminar hall on Monday at 3:00 PM.",
                c.get("Academic"), Priority.IMPORTANT, principal, now.plusDays(7)), target(TargetType.ROLE, Role.FACULTY, null, null, null));

        publish(notice("HOD and faculty coordination meeting",
                "A joint meeting of HODs and faculty will be held to plan the upcoming semester.",
                c.get("Academic"), Priority.NORMAL, principal, now.plusDays(7)),
                target(TargetType.ROLE, Role.HOD, null, null, null), target(TargetType.ROLE, Role.FACULTY, null, null, null));

        publish(notice("Computer Engineering department meeting",
                "All Computer Engineering faculty are requested to attend the department meeting on Wednesday at 2:00 PM.",
                c.get("Academic"), Priority.NORMAL, hodCs, now.plusDays(4)),
                target(TargetType.ROLE_DEPARTMENT, Role.FACULTY, d.get("CS"), null, null));

        publish(notice("Semester 6 end-semester examination timetable",
                "The end-semester examination for Computer Engineering semester 6 begins on the 20th. Collect hall tickets from the office.",
                c.get("Examination"), Priority.IMPORTANT, hodCs, now.plusDays(20)),
                target(TargetType.SEMESTER, Role.STUDENT, d.get("CS"), 6, null));

        publish(notice("Campus placement drive",
                "A campus placement drive for final-year students will be held next week. Register on the placement portal by Friday.",
                c.get("Placement"), Priority.IMPORTANT, principal, now.plusDays(10)),
                target(TargetType.ROLE, Role.STUDENT, null, null, null));

        publish(notice("Machine learning workshop",
                "A two-day hands-on machine learning workshop will be held in Lab 3. Bring your laptops.",
                c.get("Workshop"), Priority.NORMAL, hodCs, now.plusDays(14)),
                target(TargetType.ROLE_DEPARTMENT, Role.STUDENT, d.get("CS"), null, null));

        publish(notice("Post-matric scholarship applications open",
                "Eligible students can apply for the post-matric scholarship before the end of this month.",
                c.get("Scholarship"), Priority.NORMAL, principal, now.plusDays(25)),
                target(TargetType.ROLE, Role.STUDENT, null, null, null));

        Notice pending = notice("Class 6A lab assignment submission",
                "Submit the DBMS lab assignment by Monday. Late submissions will not be accepted.",
                c.get("Academic"), Priority.NORMAL, facCs, now.plusDays(6));
        addTargets(pending, target(TargetType.CLASS, Role.STUDENT, d.get("CS"), 6, "A"));
        pending.setStatus(NoticeStatus.PENDING_APPROVAL);
        noticeRepository.save(pending);

        Notice scheduled = notice("Annual sports day",
                "The annual sports day will be held on the main ground. Participation details will follow.",
                c.get("Sports"), Priority.NORMAL, principal, now.plusDays(30));
        addTargets(scheduled, target(TargetType.COLLEGE, null, null, null, null));
        scheduled.setPublishDate(now.plusDays(2));
        scheduled.setStatus(NoticeStatus.SCHEDULED);
        noticeRepository.save(scheduled);

        log.info("Seed complete. Sign in with any seeded email and password '{}'. Admin: {}, student: {}",
                DEMO_PASSWORD, admin.getEmail(), stuCs6a.getEmail());
        log.info("Other accounts: {}, {}, {}, {}", principal.getEmail(), hodCs.getEmail(), hodMech.getEmail(), facCs.getEmail());
    }

    private User user(String name, String email, Role role, Department dept, Integer sem, String cls) {
        return userRepository.save(User.builder().fullName(name).email(email).password(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role).department(dept).semester(sem).className(cls).active(true).build());
    }

    private User pending(String name, String email, Role role, Department dept, Integer sem, String cls) {
        return userRepository.save(User.builder().fullName(name).email(email).password(passwordEncoder.encode(DEMO_PASSWORD))
                .role(role).department(dept).semester(sem).className(cls).status(AccountStatus.PENDING).build());
    }

    private void publicNotice(String title, String desc, NoticeCategory cat, Priority p, User by, LocalDateTime expiry,
                              InstitutionScope scope) {
        Notice n = notice(title, desc, cat, p, by, expiry);
        n.setVisibility(NoticeVisibility.PUBLIC);
        n.setInstitutionScope(scope);
        publish(n);
    }

    private Notice notice(String title, String desc, NoticeCategory cat, Priority p, User by, LocalDateTime expiry) {
        return Notice.builder().title(title).description(desc).category(cat).priority(p).createdBy(by)
                .department(by.getDepartment()).expiryDate(expiry).status(NoticeStatus.DRAFT).build();
    }

    private NoticeTarget target(TargetType type, Role role, Department dept, Integer sem, String cls) {
        return NoticeTarget.builder().targetType(type).targetRole(role).department(dept).semester(sem).className(cls).build();
    }

    private void addTargets(Notice n, NoticeTarget... targets) {
        n.replaceTargets(new ArrayList<>(Arrays.asList(targets)));
    }

    private void publish(Notice n, NoticeTarget... targets) {
        addTargets(n, targets);
        LocalDateTime now = LocalDateTime.now();
        n.setStatus(NoticeStatus.PUBLISHED);
        n.setPublishDate(now);
        n.setPublishedAt(now);
        noticeRepository.save(n);
        notificationService.notifyPublished(n);
    }
}
