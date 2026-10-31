package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

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
    void skipsSilentlyWhenSmtpNotConfigured() {
        EmailService service = serviceWith(null, "");
        service.sendDataExportReady("user@example.com", "http://x");  // must not throw
    }
}
