package com.porquinho.controller;

import com.porquinho.config.TestWebSecurityConfig;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.service.CategoryOptions;
import com.porquinho.service.CategoryService;
import com.porquinho.service.CategorySuggestions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // ---- Story 3.5: options, create, update, remove ----

    private Category saved(String name, UUID parentId, String color, String icon) {
        Category category = new Category(userId, name, parentId, color, icon, false);
        category.setId(UUID.randomUUID());
        category.setCreatedAt(OffsetDateTime.parse("2026-11-02T12:00:00Z"));
        return category;
    }

    @Test
    void optionsContainEveryColorAndIconOfTheSuggestionsCatalog() throws Exception {
        for (CategorySuggestions.Group group : CategorySuggestions.GROUPS) {
            assertTrue(CategoryOptions.isAllowedColor(group.colorHex()), group.colorHex());
            assertTrue(CategoryOptions.isAllowedIcon(group.iconName()), group.iconName());
            for (CategorySuggestions.Suggestion s : group.suggestions()) {
                assertTrue(CategoryOptions.isAllowedColor(s.colorHex()), s.colorHex());
                assertTrue(CategoryOptions.isAllowedIcon(s.iconName()), s.iconName());
            }
        }
        mockMvc.perform(get("/api/v1/categories/options").with(authentication(authToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.colors", hasSize(CategoryOptions.COLORS.size())))
            .andExpect(jsonPath("$.colors[0]").value("#2563EB"))
            .andExpect(jsonPath("$.icons", hasSize(CategoryOptions.ICONS.size())))
            .andExpect(jsonPath("$.icons[1]").value("house"));
    }

    @Test
    void createReturns201WithTheStrippedName() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(categoryService.create(userId, "Pets", "#F59E0B", "paw-print", parentId))
            .thenReturn(saved("Pets", parentId, "#F59E0B", "paw-print"));

        mockMvc.perform(post("/api/v1/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\u00a0 Pets \",\"colorHex\":\"#F59E0B\",\"iconName\":\"paw-print\",\"parentId\":\"" + parentId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.categoryName").value("Pets"))
            .andExpect(jsonPath("$.parentCategoryId").value(parentId.toString()))
            .andExpect(jsonPath("$.systemDefault").value(false));
    }

    @Test
    void createWithoutColorOrIconPassesNullsToTheService() throws Exception {
        UUID parentId = UUID.randomUUID();
        when(categoryService.create(userId, "Pets", null, null, parentId))
            .thenReturn(saved("Pets", parentId, "#2563EB", CategoryOptions.DEFAULT_ICON));

        mockMvc.perform(post("/api/v1/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pets\",\"parentId\":\"" + parentId + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.colorHex").value("#2563EB"))
            .andExpect(jsonPath("$.iconName").value("folder"))
            .andExpect(jsonPath("$.systemDefault").value(false));
    }

    @Test
    void createWithInvalidFieldsIs400WithoutCallingTheService() throws Exception {
        String parent = UUID.randomUUID().toString();
        String[][] cases = {
            {"{\"name\":\"  \",\"colorHex\":\"#F59E0B\",\"iconName\":\"gift\",\"parentId\":\"" + parent + "\"}",
                CategoryService.NAME_REQUIRED_MESSAGE},
            {"{\"name\":\"" + "x".repeat(51) + "\",\"colorHex\":\"#F59E0B\",\"iconName\":\"gift\",\"parentId\":\"" + parent + "\"}",
                CategoryService.NAME_TOO_LONG_MESSAGE},
            {"{\"name\":\"Pets\",\"colorHex\":\"#123456\",\"iconName\":\"gift\",\"parentId\":\"" + parent + "\"}",
                CategoryService.COLOR_INVALID_MESSAGE},
            {"{\"name\":\"Pets\",\"colorHex\":\"red\",\"iconName\":\"gift\",\"parentId\":\"" + parent + "\"}",
                CategoryService.COLOR_INVALID_MESSAGE},
            {"{\"name\":\"Pets\",\"colorHex\":\"#F59E0B\",\"iconName\":\"skull\",\"parentId\":\"" + parent + "\"}",
                CategoryService.ICON_INVALID_MESSAGE},
            {"{\"name\":\"Pets\",\"colorHex\":\"#F59E0B\",\"iconName\":\"gift\"}",
                CategoryService.PARENT_REQUIRED_MESSAGE},
        };
        for (String[] c : cases) {
            mockMvc.perform(post("/api/v1/categories").with(authentication(authToken))
                    .contentType(MediaType.APPLICATION_JSON).content(c[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(c[1]));
        }
        verifyNoInteractions(categoryService);
    }

    @Test
    void createWithAnInvalidParentIs400AndARepeatedNameIs409() throws Exception {
        UUID parentId = UUID.randomUUID();
        String body = "{\"name\":\"Pets\",\"colorHex\":\"#F59E0B\",\"iconName\":\"gift\",\"parentId\":\"" + parentId + "\"}";
        when(categoryService.create(eq(userId), any(), any(), any(), eq(parentId)))
            .thenThrow(new BadRequestException(CategoryService.PARENT_INVALID_MESSAGE))
            .thenThrow(new ConflictException(CategoryService.DUPLICATE_NAME_MESSAGE));

        mockMvc.perform(post("/api/v1/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(CategoryService.PARENT_INVALID_MESSAGE));
        mockMvc.perform(post("/api/v1/categories").with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Já existe uma categoria com esse nome"));
    }

    @Test
    void patchPassesOnlyTheSentFieldsAndReturns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.update(userId, id, "Diversão", null, null))
            .thenReturn(saved("Diversão", UUID.randomUUID(), "#EC4899", "party-popper"));

        mockMvc.perform(patch("/api/v1/categories/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Diversão \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryName").value("Diversão"));

        verify(categoryService).update(userId, id, "Diversão", null, null);
    }

    @Test
    void patchWithInvalidOrNoFieldsIs400() throws Exception {
        UUID id = UUID.randomUUID();
        String[][] cases = {
            {"{\"name\":\" \"}", CategoryService.NAME_REQUIRED_MESSAGE},
            {"{\"colorHex\":\"#000000\"}", CategoryService.COLOR_INVALID_MESSAGE},
            {"{\"iconName\":\"skull\"}", CategoryService.ICON_INVALID_MESSAGE},
            {"{}", CategoryService.NOTHING_TO_UPDATE_MESSAGE},
        };
        for (String[] c : cases) {
            mockMvc.perform(patch("/api/v1/categories/" + id).with(authentication(authToken))
                    .contentType(MediaType.APPLICATION_JSON).content(c[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(c[1]));
        }
        verifyNoInteractions(categoryService);
    }

    @Test
    void patchOfAnotherUsersCategoryIs404AndARepeatedNameIs409() throws Exception {
        UUID id = UUID.randomUUID();
        when(categoryService.update(eq(userId), eq(id), any(), isNull(), isNull()))
            .thenThrow(new ResourceNotFoundException(CategoryService.NOT_FOUND_MESSAGE))
            .thenThrow(new ConflictException(CategoryService.DUPLICATE_NAME_MESSAGE));

        mockMvc.perform(patch("/api/v1/categories/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/categories/" + id).with(authentication(authToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
            .andExpect(status().isConflict());
    }

    @Test
    void deleteReturns204Or404Or409() throws Exception {
        UUID ok = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        UUID last = UUID.randomUUID();
        doThrow(new ResourceNotFoundException(CategoryService.NOT_FOUND_MESSAGE)).when(categoryService).delete(userId, missing);
        doThrow(new ConflictException(CategoryService.LAST_CHILD_MESSAGE)).when(categoryService).delete(userId, last);

        mockMvc.perform(delete("/api/v1/categories/" + ok).with(authentication(authToken)))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/categories/" + missing).with(authentication(authToken)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/categories/" + last).with(authentication(authToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("Mantenha ao menos uma categoria"));
        verify(categoryService).delete(userId, ok);
    }
}
