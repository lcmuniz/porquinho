package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.dto.CreateGoalRequest;
import com.porquinho.dto.GoalProgressResponse;
import com.porquinho.dto.UpdateGoalAllocationsRequest;
import com.porquinho.entity.Goal;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @Test
    void postCleansTheDescriptionAndBlankBecomesNull() throws Exception {
        Goal created = goal("Viagem", "100.00", null, null);
        created.setDescription("Viagem");
        when(goalService.create(eq(userId), any(CreateGoalRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viagem\",\"targetAmount\":100,\"description\":\"  Viagem  \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.description").value("Viagem"))
            .andExpect(jsonPath("$.currentAmount").value(0.0))
            .andExpect(jsonPath("$.template").value(org.hamcrest.Matchers.nullValue()));
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Viagem\",\"targetAmount\":100,\"description\":\"   \"}"))
            .andExpect(status().isCreated());

        ArgumentCaptor<CreateGoalRequest> captor = ArgumentCaptor.forClass(CreateGoalRequest.class);
        verify(goalService, times(2)).create(eq(userId), captor.capture());
        assertThat(captor.getAllValues().get(0).description()).isEqualTo("Viagem");
        assertThat(captor.getAllValues().get(1).description()).isNull();
    }

    @Test
    void postPassesTheTemplateAndReturnsIt() throws Exception {
        Goal created = goal("Férias", "10000.00", null, null);
        created.setTemplate("VACATION");
        when(goalService.create(eq(userId), any(CreateGoalRequest.class))).thenReturn(created);

        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Férias\",\"targetAmount\":10000,\"template\":\"VACATION\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.template").value("VACATION"));

        ArgumentCaptor<CreateGoalRequest> captor = ArgumentCaptor.forClass(CreateGoalRequest.class);
        verify(goalService).create(eq(userId), captor.capture());
        assertThat(captor.getValue().template()).isEqualTo("VACATION");
    }

    @Test
    void unknownTemplateIs400() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Meta\",\"targetAmount\":100,\"template\":\"FOO\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.template").value("Modelo de meta inválido"));

        verifyNoInteractions(goalService);
    }

    @Test
    void descriptionLongerThan500Is400OnTheField() throws Exception {
        mockMvc.perform(post("/api/v1/goals").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Meta\",\"targetAmount\":100,\"description\":\"" + "a".repeat(501) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.description").value("Use no máximo 500 caracteres na descrição"));

        verifyNoInteractions(goalService);
    }

    @Test
    void putAllocationReturns200WithTheGoal() throws Exception {
        Goal updated = goal("Viagem", "8000.00", null, "700.00");
        when(goalService.updateAllocation(userId, updated.getId(), new BigDecimal("700"))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/goals/" + updated.getId() + "/allocation").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyAllocation\":700}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.monthlyAllocation").value(700.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"monthlyAllocation\":-1}", "{}", "{\"monthlyAllocation\":1.001}"})
    void invalidAllocationIs400OnTheField(String content) throws Exception {
        mockMvc.perform(put("/api/v1/goals/" + UUID.randomUUID() + "/allocation").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(content))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.monthlyAllocation").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void putAllocationsReturns200WithTheGoals() throws Exception {
        Goal a = goal("Viagem", "8000.00", null, "300.00");
        Goal b = goal("Carro", "9000.00", null, "400.00");
        when(goalService.updateAllocations(eq(userId), any())).thenReturn(List.of(a, b));

        mockMvc.perform(put("/api/v1/goals/allocations").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"goalId\":\"" + a.getId() + "\",\"monthlyAllocation\":300},"
                    + "{\"goalId\":\"" + b.getId() + "\",\"monthlyAllocation\":400}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[1].monthlyAllocation").value(400.0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UpdateGoalAllocationsRequest.Item>> items = ArgumentCaptor.forClass(List.class);
        verify(goalService).updateAllocations(eq(userId), items.capture());
        assertThat(items.getValue()).containsExactly(
            new UpdateGoalAllocationsRequest.Item(a.getId(), new BigDecimal("300")),
            new UpdateGoalAllocationsRequest.Item(b.getId(), new BigDecimal("400")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"items\":[]}", "{}", "{\"items\":[{\"goalId\":\"%s\",\"monthlyAllocation\":-1}]}",
        "{\"items\":[{\"goalId\":\"%s\",\"monthlyAllocation\":1.001}]}", "{\"items\":[{\"goalId\":\"%s\"}]}",
        "{\"items\":[{\"monthlyAllocation\":10}]}"})
    void invalidAllocationsBatchIs400(String content) throws Exception {
        mockMvc.perform(put("/api/v1/goals/allocations").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(content.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(goalService);
    }

    @Test
    void invalidItemErrorIsKeyedByItsIndexForTheFrontend() throws Exception {
        mockMvc.perform(put("/api/v1/goals/allocations").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"goalId\":\"" + UUID.randomUUID() + "\",\"monthlyAllocation\":-1}]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors['items[0].monthlyAllocation']").value("O aporte mensal não pode ser negativo"));
    }

    @Test
    void repeatedGoalInTheBatchIs400AndAnotherUsersGoalIs404() throws Exception {
        UUID id = UUID.randomUUID();
        String content = "{\"items\":[{\"goalId\":\"" + id + "\",\"monthlyAllocation\":10}]}";
        when(goalService.updateAllocations(eq(userId), any()))
            .thenThrow(new BadRequestException("A mesma meta foi informada mais de uma vez"))
            .thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(put("/api/v1/goals/allocations").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(content))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/goals/allocations").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(content))
            .andExpect(status().isNotFound());
    }

    @Test
    void postContributionReturns201WithTheGoal() throws Exception {
        Goal updated = goal("Viagem", "8000.00", null, null);
        updated.setCurrentAmount(new BigDecimal("150.00"));
        when(goalService.contribute(eq(userId), eq(updated.getId()), eq(new BigDecimal("50")), eq("127.0.0.1"))).thenReturn(updated);

        mockMvc.perform(post("/api/v1/goals/" + updated.getId() + "/contributions").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.currentAmount").value(150.0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"amount\":0}", "{\"amount\":-5}", "{}", "{\"amount\":1.001}"})
    void invalidContributionIs400OnTheField(String content) throws Exception {
        mockMvc.perform(post("/api/v1/goals/" + UUID.randomUUID() + "/contributions").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(content))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.amount").exists());

        verifyNoInteractions(goalService);
    }

    @Test
    void anotherUsersGoalIs404() throws Exception {
        UUID goalId = UUID.randomUUID();
        when(goalService.contribute(eq(userId), eq(goalId), any(), eq("127.0.0.1"))).thenThrow(new ResourceNotFoundException("Meta não encontrada"));
        when(goalService.updateAllocation(eq(userId), eq(goalId), any())).thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(post("/api/v1/goals/" + goalId + "/contributions").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/goals/" + goalId + "/allocation").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyAllocation\":50}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getSummaryReturnsTheTotalAvailableOfTheJwtUser() throws Exception {
        when(goalService.totalAvailable(userId)).thenReturn(new BigDecimal("1234.56"));

        mockMvc.perform(get("/api/v1/goals/summary").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAvailable").value(1234.56));
    }

    @Test
    void getProgressReturnsTheMonthlySeries() throws Exception {
        UUID goalId = UUID.randomUUID();
        when(goalService.progress(userId, goalId)).thenReturn(new GoalProgressResponse(List.of(
            new GoalProgressResponse.Point("2026-10", new BigDecimal("100.00")),
            new GoalProgressResponse.Point("2026-11", new BigDecimal("300.00")))));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/progress").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.points.length()").value(2))
            .andExpect(jsonPath("$.points[0].month").value("2026-10"))
            .andExpect(jsonPath("$.points[1].amount").value(300.0));
    }

    @Test
    void getProgressOfAnotherUsersGoalIs404() throws Exception {
        UUID goalId = UUID.randomUUID();
        when(goalService.progress(userId, goalId)).thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(get("/api/v1/goals/" + goalId + "/progress").with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }

    // Story 8.7

    @Test
    void postContributionThatReachesTheTargetReturnsTheCompletedGoal() throws Exception {
        Goal updated = goal("Viagem", "1000.00", null, null);
        updated.setCurrentAmount(new BigDecimal("1000.00"));
        updated.setCompleted(true);
        updated.setCompletedAt(OffsetDateTime.parse("2026-11-14T12:00:00Z"));
        when(goalService.contribute(eq(userId), eq(updated.getId()), any(), eq("127.0.0.1"))).thenReturn(updated);

        mockMvc.perform(post("/api/v1/goals/" + updated.getId() + "/contributions").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":100}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").exists());
    }

    @Test
    void postCompleteAndReopenReturn200WithTheGoal() throws Exception {
        Goal done = goal("Viagem", "1000.00", null, null);
        done.setCompleted(true);
        done.setCompletedAt(OffsetDateTime.parse("2026-11-14T12:00:00Z"));
        Goal reopened = goal("Viagem", "1000.00", null, null);
        when(goalService.complete(eq(userId), eq(done.getId()), eq("127.0.0.1"))).thenReturn(done);
        when(goalService.reopen(eq(userId), eq(reopened.getId()), eq("127.0.0.1"))).thenReturn(reopened);

        mockMvc.perform(post("/api/v1/goals/" + done.getId() + "/complete").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(true))
            .andExpect(jsonPath("$.completedAt").exists());
        mockMvc.perform(post("/api/v1/goals/" + reopened.getId() + "/reopen").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.completed").value(false))
            .andExpect(jsonPath("$.completedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void completeAndReopenConflictsAre409AndAnotherUsersGoalIs404() throws Exception {
        UUID goalId = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        when(goalService.complete(eq(userId), eq(goalId), eq("127.0.0.1"))).thenThrow(new ConflictException("Esta meta já foi concluída"));
        when(goalService.reopen(eq(userId), eq(goalId), eq("127.0.0.1"))).thenThrow(new ConflictException("Esta meta não está concluída"));
        when(goalService.contribute(eq(userId), eq(goalId), any(), eq("127.0.0.1"))).thenThrow(new ConflictException("Esta meta já foi concluída"));
        when(goalService.complete(eq(userId), eq(foreign), eq("127.0.0.1"))).thenThrow(new ResourceNotFoundException("Meta não encontrada"));
        when(goalService.reopen(eq(userId), eq(foreign), eq("127.0.0.1"))).thenThrow(new ResourceNotFoundException("Meta não encontrada"));

        mockMvc.perform(post("/api/v1/goals/" + goalId + "/complete").with(authentication(authToken)))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/reopen").with(authentication(authToken)))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/goals/" + goalId + "/contributions").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":50}"))
            .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/goals/" + foreign + "/complete").with(authentication(authToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/goals/" + foreign + "/reopen").with(authentication(authToken)))
            .andExpect(status().isNotFound());
    }
}
