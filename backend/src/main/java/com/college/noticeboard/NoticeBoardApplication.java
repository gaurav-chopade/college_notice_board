package com.college.noticeboard;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
@EnableAsync
public class NoticeBoardApplication {

    @Value("${app.timezone:Asia/Kolkata}")
    private String timezone;

    public static void main(String[] args) {
        SpringApplication.run(NoticeBoardApplication.class, args);
    }

    @PostConstruct
    void configureTimezone() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
    }
}
