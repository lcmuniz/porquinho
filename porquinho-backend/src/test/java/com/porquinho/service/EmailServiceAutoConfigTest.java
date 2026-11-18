package com.porquinho.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

/** With the real Boot auto-configuration: application.yml turns an unset SMTP_HOST into an empty spring.mail.host. */
class EmailServiceAutoConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
        .withBean(EmailService.class);

    @Test
    void emptyHostStillCreatesAJavaMailSenderButIsNotConfigured() {
        runner.withPropertyValues("spring.mail.host=").run(ctx -> {
            assertThat(ctx).hasSingleBean(JavaMailSender.class);
            assertThat(ctx.getBean(EmailService.class).isConfigured()).isFalse();
        });
    }

    @Test
    void realHostIsConfigured() {
        runner.withPropertyValues("spring.mail.host=smtp.example.com").run(ctx ->
            assertThat(ctx.getBean(EmailService.class).isConfigured()).isTrue());
    }
}
