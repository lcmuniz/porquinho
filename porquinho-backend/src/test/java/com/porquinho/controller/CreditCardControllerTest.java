package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.CreateCreditCardRequest;
import com.porquinho.entity.CreditCard;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.CreditCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CreditCardController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class CreditCardControllerTest {

    private static final String DUE_DAY_MESSAGE = "Informe um dia entre 1 e 31";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CreditCardService creditCardService;

    private UUID userId;
    private TestingAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .subject(userId.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build();
        authToken = new TestingAuthenticationToken(jwt, null, "ROLE_USER");
    }

    private CreditCard card(String name, String limit, int dueDay) {
        CreditCard c = new CreditCard(userId, name, "Nubank", new BigDecimal(limit), dueDay);
        c.setId(UUID.randomUUID());
        c.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:00Z"));
        return c;
    }

    private String body(String name, String bank, String limit, String dueDay) {
        return "{\"cardName\":" + name + ",\"bankName\":" + bank + ",\"cardLimit\":" + limit
            + ",\"dueDay\":" + dueDay + "}";
    }

    @Test
    void postCreatesTheCardForTheJwtUserAndReturns201() throws Exception {
        when(creditCardService.create(eq(userId), any(CreateCreditCardRequest.class)))
            .thenReturn(card("Nubank Roxinho", "5000.00", 10));

        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\" Nubank Roxinho \"", "\"Nubank\"", "5000.00", "10")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.cardName").value("Nubank Roxinho"))
            .andExpect(jsonPath("$.bankName").value("Nubank"))
            .andExpect(jsonPath("$.cardLimit").value(5000.0))
            .andExpect(jsonPath("$.dueDay").value(10));

        ArgumentCaptor<CreateCreditCardRequest> captor = ArgumentCaptor.forClass(CreateCreditCardRequest.class);
        verify(creditCardService).create(eq(userId), captor.capture());
        assertThat(captor.getValue().cardName()).isEqualTo("Nubank Roxinho");
        assertThat(captor.getValue().dueDayValue()).isEqualTo(10);
    }

    @Test
    void zeroLimitAndDays1And31AreAccepted() throws Exception {
        when(creditCardService.create(eq(userId), any(CreateCreditCardRequest.class))).thenReturn(card("Cartão", "0.00", 1));

        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "0", "1")))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "0", "31")))
            .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "32", "-1", "10.5", "null"})
    void dueDayOutside1To31OrNotAnIntegerIs400(String dueDay) throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "100.00", dueDay)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.dueDay").value(DUE_DAY_MESSAGE));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void negativeLimitIs400WithTheMessageOnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "-0.01", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardLimit").value("O limite não pode ser negativo"));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void missingLimitIs400() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "null", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardLimit").value("Informe o limite do cartão (use 0,00 se não souber)"));
    }

    @Test
    void limitWithThreeDecimalsOrOutsideDecimal15_2Is400() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "10.123", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardLimit").exists());
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "10000000000000.00", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardLimit").exists());

        verifyNoInteractions(creditCardService);
    }

    @Test
    void blankNameIs400WithThePtBrMessageOnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"  \"", "null", "0", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Informe o nome do cartão"))
            .andExpect(jsonPath("$.errors.cardName").value("Informe o nome do cartão"));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void nameMadeOfInvisibleCharactersIs400() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"\u200b\ufeff\"", "null", "0", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardName").value("Informe o nome do cartão"));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void nameOrBankLongerThan100Is400() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"" + "a".repeat(101) + "\"", "\"" + "b".repeat(101) + "\"", "0", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardName").exists())
            .andExpect(jsonPath("$.errors.bankName").exists());
    }

    @Test
    void nonNumericDueDayIs400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/credit-cards").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "0", "\"abc\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Dados inválidos. Confira os campos e tente novamente."));
    }

    @Test
    void getListsTheJwtUsersCards() throws Exception {
        when(creditCardService.listActive(userId)).thenReturn(List.of(card("Nubank", "5000.00", 10), card("Inter", "0.00", 31)));

        mockMvc.perform(get("/api/v1/credit-cards").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].cardName").value("Nubank"))
            .andExpect(jsonPath("$[1].dueDay").value(31));
    }

    @Test
    void deleteReturns204() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/credit-cards/" + id).with(authentication(authToken)))
            .andExpect(status().isNoContent());

        verify(creditCardService).delete(userId, id);
    }

    @Test
    void deleteOfAnotherUsersCardIs404() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Cartão não encontrado")).when(creditCardService).delete(userId, id);

        mockMvc.perform(delete("/api/v1/credit-cards/" + id).with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }

    // Story 4.3: PUT /api/v1/credit-cards/{id}

    @Test
    void putUpdatesTheJwtUsersCardAndReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        CreditCard updated = card("Nubank", "6000.00", 20);
        updated.setId(id);
        when(creditCardService.update(eq(userId), eq(id), any(CreateCreditCardRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/credit-cards/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\" Nubank \"", "\"Nubank\"", "6000.00", "20")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.cardLimit").value(6000.0))
            .andExpect(jsonPath("$.dueDay").value(20));

        ArgumentCaptor<CreateCreditCardRequest> captor = ArgumentCaptor.forClass(CreateCreditCardRequest.class);
        verify(creditCardService).update(eq(userId), eq(id), captor.capture());
        assertThat(captor.getValue().cardName()).isEqualTo("Nubank");
        assertThat(captor.getValue().dueDayValue()).isEqualTo(20);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "32", "10.5", "null"})
    void putWithDueDayOutside1To31Is400(String dueDay) throws Exception {
        mockMvc.perform(put("/api/v1/credit-cards/" + UUID.randomUUID()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "100.00", dueDay)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.dueDay").value(DUE_DAY_MESSAGE));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void putWithBlankNameOrNegativeLimitIs400() throws Exception {
        mockMvc.perform(put("/api/v1/credit-cards/" + UUID.randomUUID()).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"  \"", "null", "-1.00", "10")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.cardName").value("Informe o nome do cartão"))
            .andExpect(jsonPath("$.errors.cardLimit").value("O limite não pode ser negativo"));

        verifyNoInteractions(creditCardService);
    }

    @Test
    void putOfAnotherUsersOrRemovedCardIs404() throws Exception {
        UUID id = UUID.randomUUID();
        when(creditCardService.update(eq(userId), eq(id), any(CreateCreditCardRequest.class)))
            .thenThrow(new ResourceNotFoundException("Cartão não encontrado"));

        mockMvc.perform(put("/api/v1/credit-cards/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Cartão\"", "null", "100.00", "10")))
            .andExpect(status().isNotFound());
    }
}
