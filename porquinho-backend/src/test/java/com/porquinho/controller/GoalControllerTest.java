package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.entity.Goal;
import com.porquinho.service.GoalService;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GoalController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class GoalControllerTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoalService goalService;

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

    private Goal goal(String name, String target, LocalDate date, String allocation) {
        Goal g = new Goal(userId, name, new BigDecimal(target), date, allocation == null ? null : new BigDecimal(allocation));
        g.setId(UUID.randomUUID());
        g.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:00Z"));
        return g;
    }

    private String body(String name, String target, String date, String allocation) {
        return "{\"name\":" + name + ",\"targetAmount\":" + target + ",\"targetDate\":" + date
            + ",\"monthlyAllocation\":" + allocation + "}";
    }

    private static String quoted(LocalDate date) {
        return "\"" + date + "\"";
    }

    @Test
    void postCreatesTheGoalForTheJwtUserAndReturns201WithCurrentAmountZero() throws Exception {
        LocalDate date = LocalDate.now(SAO_PAULO).plusYears(1);
        when(goalService.create(eq(userId), any(CreateGoalRequest.class)))
            .thenReturn(goal("Reserva de Emergência", "25000.00", date, "2083.34"));

        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\" Reserva de Emergência \"", "25000.00", quoted(date), "2083.34")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Reserva de Emergência"))
            .andExpect(jsonPath("$.targetAmount").value(25000.0))
            .andExpect(jsonPath("$.currentAmount").value(0.0))
            .andExpect(jsonPath("$.targetDate").value(date.toString()))
            .andExpect(jsonPath("$.monthlyAllocation").value(2083.34))
            .andExpect(jsonPath("$.completed").value(false));

        ArgumentCaptor<CreateGoalRequest> captor = ArgumentCaptor.forClass(CreateGoalRequest.class);
        verify(goalService).create(eq(userId), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("Reserva de Emergência");
        assertThat(captor.getValue().targetDate()).isEqualTo(date);
    }

    @Test
    void todayInSaoPauloAndNoDateOrAllocationAreAccepted() throws Exception {
        when(goalService.create(eq(userId), any(CreateGoalRequest.class))).thenReturn(goal("Meta", "1.00", null, null));

        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "0.01", quoted(LocalDate.now(SAO_PAULO)), "0")))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "1", "null", "null")))
            .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.00"})
    void targetAmountNotAboveZeroIs400OnTheField(String target) throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", target, "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetAmount").value("O valor-alvo precisa ser maior que zero"));

        verifyNoInteractions(goalService);
    }

    @Test
    void missingTargetAmountOrOutsideDecimal15_2Is400() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "null", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetAmount").value("Informe o valor-alvo da meta"));
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "10.123", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetAmount").exists());
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "10000000000000.00", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetAmount").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void pastDateIs400OnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "100", quoted(LocalDate.now(SAO_PAULO).minusDays(1)), "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.targetDate").value("Escolha uma data de hoje em diante"));

        verifyNoInteractions(goalService);
    }

    @Test
    void negativeOrThreeDecimalAllocationIs400OnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "100", "null", "-0.01")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.monthlyAllocation").value("O aporte mensal não pode ser negativo"));
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "100", "null", "1.001")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.monthlyAllocation").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void blankOrTooLongNameIs400WithThePtBrMessageOnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"  \"", "100", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Informe o nome da meta"));
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"" + "a".repeat(101) + "\"", "100", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Use no máximo 100 caracteres no nome da meta"));

        verifyNoInteractions(goalService);
    }

    @Test
    void invalidDateFormatIs400ProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"Meta\"", "100", "\"31/12/2027\"", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Dados inválidos. Confira os campos e tente novamente."));
    }

    @Test
    void getListsTheJwtUsersGoals() throws Exception {
        when(goalService.listActive(userId)).thenReturn(List.of(
            goal("Reserva de Emergência", "25000.00", null, null), goal("Viagem", "8000.00", null, "500.00")));

        mockMvc.perform(get("/api/v1/goals").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Reserva de Emergência"))
            .andExpect(jsonPath("$[1].monthlyAllocation").value(500.0));
    }

    @Test
    void nameMadeOnlyOfInvisibleCharactersIs400() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("\"\\u200B\\uFEFF\"", "100", "null", "null")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.name").value("Informe o nome da meta"));

        verifyNoInteractions(goalService);
    }
}
