package com.whatsmine.service.email;

import com.whatsmine.model.WorkspaceSmtpConfig;
import com.whatsmine.repository.SystemSettingRepository;
import com.whatsmine.repository.WorkspaceSmtpConfigRepository;
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
    private final WorkspaceSmtpConfigRepository workspaceSmtpConfigRepository;

    public EmailApiClient(SystemSettingRepository systemSettingRepository,
                           WorkspaceSmtpConfigRepository workspaceSmtpConfigRepository) {
        this.systemSettingRepository = systemSettingRepository;
        this.workspaceSmtpConfigRepository = workspaceSmtpConfigRepository;
    }

    /** Platform-wide send (admin test email, etc.) — no workspace override consulted. */
    public void send(String toEmail, String subject, String htmlBody) {
        send(null, toEmail, subject, htmlBody);
    }

    /**
     * Sends using the workspace's own active SMTP config if one exists,
     * otherwise falls back to the admin's platform-wide SMTP settings.
     */
    public void send(Long workspaceId, String toEmail, String subject, String htmlBody) {
        Map<String, String> smtp = loadSmtpSettings(workspaceId);

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

    private Map<String, String> loadSmtpSettings(Long workspaceId) {
        if (workspaceId != null) {
            WorkspaceSmtpConfig config = workspaceSmtpConfigRepository.findByWorkspaceId(workspaceId).orElse(null);
            if (config != null && Boolean.TRUE.equals(config.getIsActive())) {
                Map<String, String> smtp = new HashMap<>();
                smtp.put("host", config.getHost());
                smtp.put("port", config.getPort() != null ? String.valueOf(config.getPort()) : null);
                smtp.put("username", config.getUsername());
                Object password = config.getSecrets() != null ? config.getSecrets().get("password") : null;
                smtp.put("password", password != null ? String.valueOf(password) : null);
                smtp.put("encryption", config.getEncryption());
                smtp.put("from_email", config.getFromEmail());
                smtp.put("from_name", config.getFromName());
                return smtp;
            }
        }

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
