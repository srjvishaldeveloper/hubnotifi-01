package com.whatsmine.service.email;

import com.whatsmine.repository.SystemSettingRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Real SMTP sending using whatever the admin has configured under
 * Admin > Email System (SystemSetting rows, group="smtp"). A JavaMailSender
 * is built fresh per send since the config is admin-editable at runtime,
 * not a fixed application.yml property. No config = the send throws,
 * loudly, rather than fabricating a fake success.
 */
@Service
public class EmailApiClient {

    private static final Logger log = LoggerFactory.getLogger(EmailApiClient.class);

    private final SystemSettingRepository systemSettingRepository;

    public EmailApiClient(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    public void send(String toEmail, String subject, String htmlBody) {
        Map<String, String> smtp = loadSmtpSettings();

        String host = smtp.get("host");
        String username = smtp.get("username");
        String password = smtp.get("password");
        String fromEmail = smtp.get("from_email");

        if (host == null || host.isBlank() || fromEmail == null || fromEmail.isBlank()) {
            throw new IllegalStateException("No SMTP server configured — set it up under Admin > Email System.");
        }

        int port = parsePort(smtp.get("port"));
        String encryption = smtp.getOrDefault("encryption", "tls");

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        if (username != null && !username.isBlank()) mailSender.setUsername(username);
        if (password != null && !password.isBlank()) mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", username != null && !username.isBlank());
        props.put("mail.smtp.starttls.enable", "tls".equalsIgnoreCase(encryption));
        props.put("mail.smtp.ssl.enable", "ssl".equalsIgnoreCase(encryption));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String fromName = smtp.get("from_name");
            if (fromName != null && !fromName.isBlank()) {
                helper.setFrom(fromEmail, fromName);
            } else {
                helper.setFrom(fromEmail);
            }
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);
        } catch (Exception e) {
            log.warn("SMTP send failed: {}", e.getMessage());
            throw new IllegalStateException("SMTP send failed: " + e.getMessage(), e);
        }
    }

    private Map<String, String> loadSmtpSettings() {
        Map<String, String> smtp = new HashMap<>();
        systemSettingRepository.findByGroup("smtp").forEach(s -> smtp.put(s.getKey(), s.getValue()));
        return smtp;
    }

    private int parsePort(String raw) {
        try {
            return raw != null ? Integer.parseInt(raw.trim()) : 587;
        } catch (NumberFormatException e) {
            return 587;
        }
    }
}
