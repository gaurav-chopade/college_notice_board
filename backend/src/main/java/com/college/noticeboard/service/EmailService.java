package com.college.noticeboard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Optional Gmail SMTP integration. Disabled unless MAIL_ENABLED=true and credentials are set.
 * Each recipient gets an individual message so addresses are never exposed to other recipients.
 */
@Slf4j
@Service
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final boolean enabled;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.enabled:false}") boolean enabled,
                        @Value("${app.mail.from:noreply@college.edu}") String from) {
        this.mailSenderProvider = mailSenderProvider;
        this.enabled = enabled;
        this.from = from;
    }

    public boolean isEnabled() {
        return enabled && mailSenderProvider.getIfAvailable() != null;
    }

    @Async("mailExecutor")
    public void sendToEach(List<String> recipients, String subject, String body) {
        if (!isEnabled()) {
            log.info("Email disabled; skipped '{}' to {} recipient(s)", subject, recipients.size());
            return;
        }
        JavaMailSender sender = mailSenderProvider.getObject();
        int sent = 0;
        for (String to : recipients) {
            try {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(from);
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                sender.send(msg);
                sent++;
            } catch (Exception e) {
                log.warn("Failed to send email to {}: {}", to, e.getMessage());
            }
        }
        log.info("Sent '{}' to {}/{} recipient(s)", subject, sent, recipients.size());
    }

    public void sendSingle(String to, String subject, String body) {
        sendToEach(List.of(to), subject, body);
    }
}
