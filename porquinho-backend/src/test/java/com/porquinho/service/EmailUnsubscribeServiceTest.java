package com.porquinho.service;

import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EmailUnsubscribeServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final EmailUnsubscribeService service =
        new EmailUnsubscribeService(mock(UserRepository.class), "segredo-de-teste", "https://api.example.com/");

    @Test
    void validTokenGivesTheUser() {
        assertThat(service.verify(service.token(userId, EmailUnsubscribeService.BUDGET_ALERT),
            EmailUnsubscribeService.BUDGET_ALERT)).isEqualTo(userId);
    }

    @Test
    void urlCarriesTheTokenOnTheApiBase() {
        assertThat(service.budgetAlertUnsubscribeUrl(userId)).isEqualTo("https://api.example.com/api/v1/email/unsubscribe?token="
            + service.token(userId, EmailUnsubscribeService.BUDGET_ALERT));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.token(userId, EmailUnsubscribeService.BUDGET_ALERT);
        String otherUser = UUID.randomUUID() + token.substring(token.indexOf('.'));
        // First signature char: the last one also holds padding bits the decoder ignores
        int first = token.indexOf('.') + 1;
        String otherSignature = token.substring(0, first) + (token.charAt(first) == 'A' ? 'B' : 'A')
            + token.substring(first + 1);

        for (String bad : new String[] {otherUser, otherSignature, token.toUpperCase(), "x", "", ".", userId + ".", null}) {
            assertThatThrownBy(() -> service.verify(bad, EmailUnsubscribeService.BUDGET_ALERT))
                .as(bad).isInstanceOf(BadRequestException.class).hasMessage("Link de descadastro inválido");
        }
    }

    @Test
    void tokenOfAnotherPurposeIsRejected() {
        String token = service.token(userId, "newsletter");

        assertThatThrownBy(() -> service.verify(token, EmailUnsubscribeService.BUDGET_ALERT))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = new EmailUnsubscribeService(mock(UserRepository.class), "outro", "https://api.example.com")
            .token(userId, EmailUnsubscribeService.BUDGET_ALERT);

        assertThatThrownBy(() -> service.verify(token, EmailUnsubscribeService.BUDGET_ALERT))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void blankSecretFallsBackToARandomOne() {
        // Without EMAIL_UNSUBSCRIBE_SECRET no fixed key exists that an attacker could know
        EmailUnsubscribeService a = new EmailUnsubscribeService(mock(UserRepository.class), "", "http://localhost:8080");
        EmailUnsubscribeService b = new EmailUnsubscribeService(mock(UserRepository.class), null, "http://localhost:8080");

        assertThat(a.token(userId, EmailUnsubscribeService.BUDGET_ALERT))
            .isNotEqualTo(b.token(userId, EmailUnsubscribeService.BUDGET_ALERT));
    }
}
