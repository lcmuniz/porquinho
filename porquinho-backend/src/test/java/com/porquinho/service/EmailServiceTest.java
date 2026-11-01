package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    @SuppressWarnings("unchecked")
    private EmailService serviceWith(JavaMailSender sender, String from) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return new EmailService(provider, from);
    }

    @Test
    void setsConfiguredFromAddress() {
        // Resend rejects messages without a From on the verified domain
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "porquinho <nao-responda@porquinho.eficaztech.cloud>");

        service.sendSubscriptionActivated("user@example.com", OffsetDateTime.of(2026, 11, 30, 13, 0, 0, 0, ZoneOffset.UTC));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getFrom()).isEqualTo("porquinho <nao-responda@porquinho.eficaztech.cloud>");
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
    }

    @Test
    void billingDateUsesSaoPauloTimezone() {
        // 2026-12-01T01:00Z is still 30/11 in Brazil
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendSubscriptionActivated("user@example.com", OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains("30/11/2026").doesNotContain("01/12/2026");
    }

    @Test
    void trialReminderSevenDaysText() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        // 30/11 13:00Z = 30/11 10:00 in Brazil
        service.sendTrialReminder("user@example.com", 7,
            OffsetDateTime.of(2026, 11, 30, 13, 0, 0, 0, ZoneOffset.UTC), "https://app.example.com/assinatura");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Porquinho - Seu trial termina em 7 dias");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText())
            .contains("termina em 7 dias (30/11/2026)")
            .contains("Assinar agora: https://app.example.com/assinatura");
    }

    @Test
    void trialReminderOneDayText() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        // 01/12 01:00Z is still 30/11 in Brazil
        service.sendTrialReminder("user@example.com", 1,
            OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC), "https://app.example.com/assinatura");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Porquinho - Seu trial termina amanhã");
        assertThat(message.getText())
            .contains("termina amanhã (30/11/2026)")
            .contains("Assinar agora: https://app.example.com/assinatura");
    }

    @Test
    void trialReminderLateRunUsesRealDayCount() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");
        OffsetDateTime end = OffsetDateTime.of(2026, 11, 30, 13, 0, 0, 0, ZoneOffset.UTC);

        service.sendTrialReminder("user@example.com", 5, end, "https://app.example.com/assinatura");
        service.sendTrialReminder("user@example.com", 0, end, "https://app.example.com/assinatura");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, times(2)).send(captor.capture());
        SimpleMailMessage five = captor.getAllValues().get(0);
        SimpleMailMessage today = captor.getAllValues().get(1);
        assertThat(five.getSubject()).isEqualTo("Porquinho - Seu trial termina em 5 dias");
        assertThat(five.getText()).contains("termina em 5 dias (30/11/2026)");
        assertThat(today.getSubject()).isEqualTo("Porquinho - Seu trial termina hoje");
        assertThat(today.getText()).contains("termina hoje (30/11/2026)");
    }

    @Test
    void trialReminderSwallowsSmtpFailure() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendTrialReminder("user@example.com", 1, OffsetDateTime.now(), "http://x/assinatura");  // must not throw
    }

    @Test
    void skipsSilentlyWhenSmtpNotConfigured() {
        EmailService service = serviceWith(null, "");
        service.sendDataExportReady("user@example.com", "http://x");  // must not throw
    }

    // ---- Story 2.4 ----

    @Test
    void subscriptionCancelledTextHasAccessUntilInSaoPauloDate() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        // 01/12 01:00Z is still 30/11 in Brazil
        service.sendSubscriptionCancelled("user@example.com", OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Porquinho - Assinatura cancelada");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText())
            .contains("acesso completo até 30/11/2026")
            .contains("Nenhuma nova cobrança")
            .doesNotContain("01/12/2026");
    }

    @Test
    void subscriptionCancelledWithoutSmtpOrWithFailureNeverThrows() {
        serviceWith(null, "x@example.com").sendSubscriptionCancelled("user@example.com", null);

        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        serviceWith(sender, "x@example.com").sendSubscriptionCancelled("user@example.com", null);
    }

    // ---- Story 2.5 ----

    @Test
    void subscriptionReactivatedTextHasNextBillingInSaoPauloDate() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        // 01/12 01:00Z is still 30/11 in Brazil
        service.sendSubscriptionReactivated("user@example.com", OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Porquinho - Assinatura reativada");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText())
            .contains("foi reativada")
            .contains("Próxima cobrança: 30/11/2026")
            .doesNotContain("01/12/2026");
    }

    @Test
    void subscriptionReactivatedWithoutNextBillingOmitsBillingLine() {
        JavaMailSender sender = mock(JavaMailSender.class);

        serviceWith(sender, "x@example.com").sendSubscriptionReactivated("user@example.com", null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText())
            .contains("foi reativada")
            .doesNotContain("Próxima cobrança");
    }

    @Test
    void subscriptionReactivatedWithoutSmtpOrWithFailureNeverThrows() {
        serviceWith(null, "x@example.com").sendSubscriptionReactivated("user@example.com", null);

        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        serviceWith(sender, "x@example.com").sendSubscriptionReactivated("user@example.com", null);
    }

    // ---- Story 2.6: payment receipt ----

    @Test
    void paymentReceiptShowsAmountPaymentDateAndNextBillingInSaoPaulo() {
        JavaMailSender sender = mock(JavaMailSender.class);

        // paid 01/11 02:00Z = 31/10 in Brazil; next billing 01/12 01:00Z = 30/11 in Brazil
        serviceWith(sender, "x@example.com").sendPaymentReceipt("user@example.com", new BigDecimal("19.90"),
            OffsetDateTime.of(2026, 11, 1, 2, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 12, 1, 1, 0, 0, 0, ZoneOffset.UTC));

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getSubject()).isEqualTo("Porquinho - Pagamento confirmado");
        assertThat(message.getTo()).containsExactly("user@example.com");
        assertThat(message.getText())
            .contains("Valor: R$ 19,90")
            .contains("Data do pagamento: 31/10/2026")
            .contains("Próxima cobrança: 30/11/2026")
            .doesNotContain("01/11/2026")
            .doesNotContain("01/12/2026");
    }

    @Test
    void paymentReceiptWithoutNextBillingOmitsBillingLine() {
        JavaMailSender sender = mock(JavaMailSender.class);

        serviceWith(sender, "x@example.com").sendPaymentReceipt("user@example.com", new BigDecimal("19.90"),
            OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC), null);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText())
            .contains("Valor: R$ 19,90")
            .doesNotContain("Próxima cobrança");
    }

    @Test
    void formatBrlUsesCommaDecimalsAndDotThousands() {
        assertThat(EmailService.formatBrl(new BigDecimal("19.90"))).isEqualTo("R$ 19,90");
        assertThat(EmailService.formatBrl(new BigDecimal("1234.5"))).isEqualTo("R$ 1.234,50");
    }

    @Test
    void paymentReceiptWithoutSmtpOrWithFailureNeverThrows() {
        OffsetDateTime paidAt = OffsetDateTime.of(2026, 11, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        serviceWith(null, "x@example.com").sendPaymentReceipt("user@example.com", new BigDecimal("19.90"), paidAt, null);

        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        serviceWith(sender, "x@example.com").sendPaymentReceipt("user@example.com", new BigDecimal("19.90"), paidAt, null);
    }

    // ---- Story 2.7 ----

    @Test
    void paymentFailedEmailHasPayLinkAndNoReason() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendPaymentFailed("user@example.com", "https://invoice.stripe.com/i/in_1", true);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Porquinho - Pagamento falhou");
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
        assertThat(captor.getValue().getText())
            .contains("https://invoice.stripe.com/i/in_1")
            .contains("Vamos tentar de novo automaticamente")
            .contains("seu acesso continua")
            .doesNotContain("última tentativa")
            .doesNotContain("Motivo");
    }

    @Test
    void lastAttemptPaymentFailedEmailDropsRetryAndWarnsOfSuspension() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendPaymentFailed("user@example.com", "https://invoice.stripe.com/i/in_1", false);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Porquinho - Pagamento falhou");
        assertThat(captor.getValue().getText())
            .contains("Esta foi a última tentativa de cobrança")
            .contains("Sem o pagamento, a assinatura será suspensa")
            .contains("https://invoice.stripe.com/i/in_1")
            .doesNotContain("Vamos tentar de novo")
            .doesNotContain("seu acesso continua");
    }

    @Test
    void immediateCancellationEmailSaysAccessEndedToday() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendSubscriptionCancelledImmediately("user@example.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Porquinho - Assinatura cancelada");
        assertThat(captor.getValue().getTo()).containsExactly("user@example.com");
        assertThat(captor.getValue().getText())
            .contains("foi cancelada")
            .contains("acesso ao app foi encerrado hoje")
            .doesNotContain("continua com acesso");
    }

    @Test
    void subscriptionSuspendedEmailHasReactivateLink() {
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendSubscriptionSuspended("user@example.com", "https://app.example.com/assinatura");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getSubject()).isEqualTo("Porquinho - Assinatura suspensa");
        assertThat(captor.getValue().getText())
            .contains("suspensa")
            .contains("Reativar assinatura: https://app.example.com/assinatura");
    }

    @Test
    void failureEmailsAreBestEffort() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("smtp down")).when(sender).send(any(SimpleMailMessage.class));
        EmailService service = serviceWith(sender, "x@example.com");

        service.sendPaymentFailed("user@example.com", "https://invoice.stripe.com/i/in_1", true);
        service.sendSubscriptionSuspended("user@example.com", "https://app.example.com/assinatura");
        service.sendSubscriptionCancelledImmediately("user@example.com");

        EmailService noSmtp = serviceWith(null, "x@example.com");
        noSmtp.sendPaymentFailed("user@example.com", "https://invoice.stripe.com/i/in_1", false);
        noSmtp.sendSubscriptionSuspended("user@example.com", "https://app.example.com/assinatura");
        noSmtp.sendSubscriptionCancelledImmediately("user@example.com");
    }
}
