package com.porquinho.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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
    private final String from;

    // ObjectProvider: JavaMailSender only exists when spring.mail.host is set (e.g. absent in tests).
    // @Autowired(required = false) on a single constructor does NOT make the parameter optional.
    // from: providers like Resend reject messages without a From on a verified domain.
    public EmailService(ObjectProvider<JavaMailSender> mailSender, @Value("${app.mail.from:}") String from) {
        this.mailSender = mailSender.getIfAvailable();
        this.from = from;
    }

    private SimpleMailMessage newMessage(String to, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(to);
        message.setSubject(subject);
        return message;
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
            SimpleMailMessage message = newMessage(email, "Porquinho - Sua exportação de dados está pronta");
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

            SimpleMailMessage message = newMessage(email, "Porquinho - Confirmação de exclusão de conta");
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

    /**
     * Send "Assinatura ativada" after the monthly plan is confirmed by Stripe (Story 2.2).
     * Best-effort: if SMTP is not configured or sending fails, logs warning and continues.
     *
     * @param email Recipient email address
     * @param nextBillingDate Date of the next (or first, if subscribed during the trial) charge; may be null
     */
    public void sendSubscriptionActivated(String email, OffsetDateTime nextBillingDate) {
        if (mailSender == null) {
            log.warn("SMTP not configured - skipping subscription activated email to {}", email);
            return;
        }

        try {
            String billingLine = nextBillingDate == null
                ? ""
                // Brazil local date: a UTC instant between 00:00 and 03:00 is still the previous day here
                : "Próxima cobrança: " + nextBillingDate
                    .atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR"))) + ".\n\n";

            SimpleMailMessage message = newMessage(email, "Porquinho - Assinatura ativada");
            message.setText(
                "Olá!\n\n" +
                "Sua assinatura mensal do Porquinho (R$ 19,90/mês) está ativa.\n\n" +
                billingLine +
                "A cobrança é feita automaticamente no cartão cadastrado.\n\n" +
                "Atenciosamente,\nEquipe Porquinho"
            );
            mailSender.send(message);
            log.info("Subscription activated email sent to {}", email);
        } catch (Exception e) {
            log.warn("Failed to send subscription activated email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send the trial-ending reminder (Story 2.3): 7 days before ("termina em 7 dias") or 1 day before
     * ("termina amanhã"). daysLeft comes from the job (Sao Paulo calendar days), so a late run says
     * the real count ("em 5 dias", "hoje") instead of a wrong one.
     * Best-effort: if SMTP is not configured or sending fails, logs warning and continues.
     *
     * @param email Recipient email address
     * @param daysLeft Calendar days until the trial ends (Sao Paulo dates)
     * @param trialEndDate When the trial ends
     * @param subscribeUrl Link to the subscription page ({frontend}/assinatura)
     */
    public void sendTrialReminder(String email, long daysLeft, OffsetDateTime trialEndDate, String subscribeUrl) {
        if (mailSender == null) {
            log.warn("SMTP not configured - skipping trial reminder email to {}", email);
            return;
        }

        try {
            String when = daysLeft <= 0 ? "hoje" : daysLeft == 1 ? "amanhã" : "em " + daysLeft + " dias";
            String endDate = trialEndDate
                .atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR")));

            SimpleMailMessage message = newMessage(email, "Porquinho - Seu trial termina " + when);
            message.setText(
                "Olá!\n\n" +
                "Seu período de teste gratuito do Porquinho termina " + when + " (" + endDate + ").\n\n" +
                "Assinando o plano mensal (R$ 19,90/mês) você continua com:\n" +
                "- Todas as suas contas, transações e categorias preservadas\n" +
                "- Acesso completo a todas as funcionalidades, sem limites\n\n" +
                "Se assinar agora, a primeira cobrança só acontece quando o trial acabar.\n\n" +
                "Assinar agora: " + subscribeUrl + "\n\n" +
                "Atenciosamente,\nEquipe Porquinho"
            );
            mailSender.send(message);
            log.info("Trial reminder ({} days left) email sent to {}", daysLeft, email);
        } catch (Exception e) {
            log.warn("Failed to send trial reminder email to {}: {}", email, e.getMessage());
        }
    }

    /**
     * Send "Assinatura cancelada" after a self-service cancel (Story 2.4), with the date until which
     * access is still valid (end of the paid period).
     * Best-effort: if SMTP is not configured or sending fails, logs warning and continues.
     *
     * @param email Recipient email address
     * @param accessUntil End of the current paid period (next_billing_date); may be null
     */
    public void sendSubscriptionCancelled(String email, OffsetDateTime accessUntil) {
        if (mailSender == null) {
            log.warn("SMTP not configured - skipping subscription cancelled email to {}", email);
            return;
        }

        try {
            String accessLine = accessUntil == null
                ? ""
                // Brazil local date: a UTC instant between 00:00 and 03:00 is still the previous day here
                : "Você continua com acesso completo até " + accessUntil
                    .atZoneSameInstant(ZoneId.of("America/Sao_Paulo"))
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.of("pt", "BR"))) + ".\n\n";

            SimpleMailMessage message = newMessage(email, "Porquinho - Assinatura cancelada");
            message.setText(
                "Olá!\n\n" +
                "Sua assinatura mensal do Porquinho foi cancelada. Nenhuma nova cobrança será feita.\n\n" +
                accessLine +
                "Seus dados continuam preservados.\n\n" +
                "Atenciosamente,\nEquipe Porquinho"
            );
            mailSender.send(message);
            log.info("Subscription cancelled email sent to {}", email);
        } catch (Exception e) {
            log.warn("Failed to send subscription cancelled email to {}: {}", email, e.getMessage());
        }
    }
}
