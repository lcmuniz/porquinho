package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Service for sending emails.
 * All email sending is best-effort: failures are logged as warnings, never propagated.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    // ObjectProvider: JavaMailSender only exists when spring.mail.host is set (e.g. absent in tests).
    // @Autowired(required = false) on a single constructor does NOT make the parameter optional.
    public EmailService(ObjectProvider<JavaMailSender> mailSender) {
        this.mailSender = mailSender.getIfAvailable();
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

    /**
     * Send confirmation that account deletion was scheduled (Story 1.7).
     * Best-effort: if SMTP is not configured or sending fails, logs warning and continues.
     *
     * @param email Recipient email address
     * @param scheduledPurgeAt Date when the account data will be permanently purged
     */
    public void sendAccountDeletionConfirmation(String email, OffsetDateTime scheduledPurgeAt) {
        if (mailSender == null) {
            log.warn("SMTP not configured - skipping account deletion confirmation email to {}", email);
            return;
        }

        try {
            String formattedDate = scheduledPurgeAt
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("pt", "BR")));

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Porquinho - Confirmação de exclusão de conta");
            message.setText(
                "Olá,\n\n" +
                "Recebemos sua solicitação de exclusão de conta no Porquinho.\n\n" +
                "Sua conta foi marcada para exclusão e seus dados serão permanentemente removidos em " + formattedDate + " (30 dias).\n\n" +
                "Durante este período de retenção, sua conta permanecerá inativa e você não poderá fazer login.\n\n" +
                "IMPORTANTE: Se você não solicitou esta exclusão, entre em contato imediatamente com nosso suporte para reverter a solicitação.\n\n" +
                "Atenciosamente,\nEquipe Porquinho"
            );
            mailSender.send(message);
            log.info("Account deletion confirmation email sent to {}", email);
        } catch (Exception e) {
            log.warn("Failed to send account deletion confirmation email to {}: {}", email, e.getMessage());
        }
    }
}
