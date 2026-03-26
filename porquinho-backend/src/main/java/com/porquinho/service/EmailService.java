package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending emails.
 * All email sending is best-effort: failures are logged as warnings, never propagated.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Send notification that data export is ready for download.
     * Best-effort: if SMTP is not configured or sending fails, logs warning and continues.
     *
     * @param email Recipient email address
     * @param downloadUrl URL for downloading the export
     */
    public void sendDataExportReady(String email, String downloadUrl) {
        if (mailSender == null) {
            log.warn("SMTP not configured - skipping data export notification email to {}", email);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Porquinho - Sua exportação de dados está pronta");
            message.setText(
                "Olá!\n\n" +
                "Sua exportação de dados do Porquinho está pronta para download.\n\n" +
                "Link de download: " + downloadUrl + "\n\n" +
                "IMPORTANTE: Este link expira em 24 horas e só pode ser usado uma vez.\n\n" +
                "Se você não solicitou esta exportação, por favor ignore este email.\n\n" +
                "Atenciosamente,\nEquipe Porquinho"
            );
            mailSender.send(message);
            log.info("Data export notification email sent to {}", email);
        } catch (Exception e) {
            log.warn("Failed to send data export notification email to {}: {}", email, e.getMessage());
        }
    }
}
