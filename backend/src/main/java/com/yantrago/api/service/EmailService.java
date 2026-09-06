package com.yantrago.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification service via SMTP (spring-boot-starter-mail).
 *
 * Per AGENTS.md rule 12: this is a production feature requiring logging + error handling.
 * Per AGENTS.md rule 20: SMTP credentials must come from environment variables, never committed.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@yantrago.com}")
    private String fromAddress;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a plain-text email.
     *
     * @param to recipient email address
     * @param subject email subject
     * @param text email body
     * @return true if sent successfully
     */
    public boolean sendEmail(String to, String subject, String text) {
        log.info("Sending email to={} subject={}", to, subject);
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
            log.info("Email sent successfully to={}", to);
            return true;
        } catch (Exception e) {
            log.error("Failed to send email to={}: {}", to, e.getMessage(), e);
            return false;
        }
    }
}
