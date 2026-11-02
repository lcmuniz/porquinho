package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Category;
import com.porquinho.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@ActiveProfiles("test")
@Import(TestWebSecurityConfig.class)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

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

    @Test
    void suggestionsReturnTheThreeGroupsWithTheTenSuggestions() throws Exception {
        mockMvc.perform(get("/api/v1/categories/suggestions").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(3)))
            .andExpect(jsonPath("$[0].key").value("essentials"))
            .andExpect(jsonPath("$[0].name").value("Essenciais"))
            .andExpect(jsonPath("$[0].iconName").value("shield-check"))
            .andExpect(jsonPath("$[0].suggestions", hasSize(4)))
            .andExpect(jsonPath("$[0].suggestions[0].key").value("housing"))
            .andExpect(jsonPath("$[0].suggestions[0].name").value("Moradia"))
            .andExpect(jsonPath("$[0].suggestions[0].iconName").value("house"))
            .andExpect(jsonPath("$[0].suggestions[0].colorHex").value("#3B82F6"))
            .andExpect(jsonPath("$[1].name").value("Variáveis"))
            .andExpect(jsonPath("$[1].suggestions", hasSize(4)))
            .andExpect(jsonPath("$[2].name").value("Financeiro"))
            .andExpect(jsonPath("$[2].suggestions", hasSize(2)))
            .andExpect(jsonPath("$[2].suggestions[1].name").value("Reserva de Emergência"));
    }

    @Test
    void listReturnsTheActiveCategoriesOfTheJwtUser() throws Exception {
        Category parent = new Category(userId, "Essenciais", null, "#2563EB", "shield-check", true);
        parent.setId(UUID.randomUUID());
        parent.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:00Z"));
        Category child = new Category(userId, "Moradia", parent.getId(), "#3B82F6", "house", true);
        child.setId(UUID.randomUUID());
        child.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:01Z"));
        when(categoryService.listActive(userId)).thenReturn(List.of(parent, child));

        mockMvc.perform(get("/api/v1/categories").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].categoryName").value("Essenciais"))
            .andExpect(jsonPath("$[0].parentCategoryId").isEmpty())
            .andExpect(jsonPath("$[0].systemDefault").value(true))
            .andExpect(jsonPath("$[1].categoryName").value("Moradia"))
            .andExpect(jsonPath("$[1].parentCategoryId").value(parent.getId().toString()))
            .andExpect(jsonPath("$[1].colorHex").value("#3B82F6"))
            .andExpect(jsonPath("$[1].iconName").value("house"));
    }
}
