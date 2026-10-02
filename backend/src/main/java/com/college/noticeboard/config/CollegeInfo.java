package com.college.noticeboard.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** College details shown on the public site, set in application.properties (app.college.*). */
@Getter
@Component
public class CollegeInfo {

    @Value("${app.college.name:ABC College}")
    private String name;

    @Value("${app.college.tagline:Stay informed about admissions, academics, examinations, events and important college announcements.}")
    private String tagline;

    @Value("${app.college.about:}")
    private String about;

    @Value("${app.college.address:}")
    private String address;

    @Value("${app.college.phone:}")
    private String phone;

    @Value("${app.college.email:}")
    private String email;

    @Value("${app.college.admission-phone:}")
    private String admissionPhone;

    @Value("${app.college.office-hours:}")
    private String officeHours;

    @Value("${app.college.engineering-about:}")
    private String engineeringAbout;

    @Value("${app.college.polytechnic-about:}")
    private String polytechnicAbout;
}
