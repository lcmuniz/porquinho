package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 3.5: customizing the categories, onboarding step 5, against the real schema (PostgreSQL via
 * Testcontainers): create/rename/recolor/remove matrix, hierarchy (max depth 1, parent of the same user),
 * case-insensitive uniqueness, the "at least one child" rule, groups (decision A), order by name and the
 * step-5 POST (advance, skip, repeat, steps 1 to 4).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryCustomizationIntegrationTest {

    private static final List<String> ALL_KEYS = List.of("housing", "food", "transport", "health", "leisure",
        "education", "clothing", "subscriptions", "investments", "emergency_fund");

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private OnboardingService onboardingService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OnboardingProgressRepository onboardingProgressRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = newUser();
    }

    private UUID newUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, auth_provider) VALUES (?, ?, 'EMAIL')", id, id + "@example.com");
        return id;
    }

    private void atStepFour() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
        accountService.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE));
        accountRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private void atStepFive(List<String> keys) {
        atStepFour();
        onboardingService.completeCategories(userId, keys, "127.0.0.1");
        onboardingProgressRepository.flush();
        categoryRepository.flush();
    }

    private Category named(UUID owner, String name) {
        return categoryService.listActive(owner).stream()
            .filter(c -> c.getCategoryName().equals(name)).findFirst().orElseThrow();
    }

    private Category named(String name) {
        return named(userId, name);
    }

    private Map<String, Object> row(UUID id) {
        return jdbc.queryForMap(
            "SELECT category_name, color_hex, icon_name, parent_category_id, is_system_default, deleted_at "
                + "FROM categories WHERE id = ?", id);
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed FROM onboarding_progress WHERE user_id = ?", userId);
    }

    private List<String> customizationAudits() {
        return jdbc.queryForList(
            "SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_customization_completed'",
            String.class, userId);
    }

    @Test
    void acceptanceRenameRecolorAddThenNextPersistsAndMovesToStepSix() {
        atStepFive(ALL_KEYS);
        Category leisure = named("Lazer");
        Category housing = named("Moradia");
        Category essentials = named("Essenciais");

        categoryService.update(userId, leisure.getId(), "Diversão", null, null);
        categoryService.update(userId, housing.getId(), null, "#64748b", null);
        Category pets = categoryService.create(userId, "Pets", "#F59E0B", "paw-print", essentials.getId());
        categoryRepository.flush();
        onboardingService.completeCustomization(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(row(leisure.getId())).containsEntry("category_name", "Diversão");
        assertThat(row(housing.getId())).containsEntry("color_hex", "#64748B").containsEntry("icon_name", "house");
        assertThat(row(pets.getId())).containsEntry("parent_category_id", essentials.getId())
            .containsEntry("is_system_default", false).containsEntry("icon_name", "paw-print");
        assertThat(progressRow()).containsEntry("current_step", 6)
            .containsEntry("steps_completed",
                "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\",\"categories_suggested\",\"categories_customized\"]");
        assertThat(customizationAudits()).containsExactly("{\"skipped\":false}");
    }

    @Test
    void skippingRecordsSkippedAndKeepsSavedEdits() {
        atStepFive(List.of("housing", "leisure"));
        categoryService.update(userId, named("Lazer").getId(), "Diversão", null, null);
        categoryRepository.flush();

        onboardingService.completeCustomization(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(named("Diversão")).isNotNull();
        assertThat(progressRow()).containsEntry("current_step", 6);
        assertThat((String) progressRow().get("steps_completed")).endsWith("\"customization_skipped\"]");
        assertThat(customizationAudits()).containsExactly("{\"skipped\":true}");
    }

    @Test
    void repeatingTheStepFivePostChangesNothingAndWritesNoAudit() {
        atStepFive(List.of("housing"));
        onboardingService.completeCustomization(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();

        onboardingService.completeCustomization(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();

        assertThat(progressRow()).containsEntry("current_step", 6);
        assertThat((String) progressRow().get("steps_completed")).doesNotContain("customization_skipped");
        assertThat(customizationAudits()).hasSize(1);
    }

    @Test
    void stepFivePostIsAConflictOnStepsOneToFour() {
        onboardingService.getOrCreate(userId);
        assertThatThrownBy(() -> onboardingService.completeCustomization(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        atStepFour();
        assertThatThrownBy(() -> onboardingService.completeCustomization(userId, false, "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(progressRow()).containsEntry("current_step", 4);
        assertThat(customizationAudits()).isEmpty();
    }

    @Test
    void renameStripsUnicodeSpacesAndRejectsEmptyOrTooLongNames() {
        atStepFive(List.of("housing"));
        UUID id = named("Moradia").getId();

        assertThat(categoryService.update(userId, id, "  Casa  ", null, null).getCategoryName()).isEqualTo("Casa");
        assertThatThrownBy(() -> categoryService.update(userId, id, "   ", null, null))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.NAME_REQUIRED_MESSAGE);
        assertThatThrownBy(() -> categoryService.update(userId, id, "x".repeat(51), null, null))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.NAME_TOO_LONG_MESSAGE);
        categoryRepository.flush();
        assertThat(row(id)).containsEntry("category_name", "Casa");
    }

    @Test
    void repeatedNameAnywhereIsAConflictIgnoringCase() {
        atStepFive(List.of("housing", "food", "leisure"));

        assertThatThrownBy(() -> categoryService.update(userId, named("Moradia").getId(), "alimentação", null, null))
            .isInstanceOf(ConflictException.class).hasMessage(CategoryService.DUPLICATE_NAME_MESSAGE);
        assertThatThrownBy(() -> categoryService.create(userId, "MORADIA", "#3B82F6", "house", named("Essenciais").getId()))
            .isInstanceOf(ConflictException.class);

        // Names are unique across every group (decision B): same name under another group is a conflict too
        assertThatThrownBy(() -> categoryService.create(userId, "Moradia", "#3B82F6", "house", named("Variáveis").getId()))
            .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> categoryService.update(userId, named("Lazer").getId(), "moradia", null, null))
            .isInstanceOf(ConflictException.class);
        // renaming to itself with another case is fine
        assertThat(categoryService.update(userId, named("Lazer").getId(), "LAZER", null, null).getCategoryName())
            .isEqualTo("LAZER");
    }

    @Test
    void aRemovedCategoryNameCanBeReused() {
        atStepFive(List.of("housing", "food"));
        categoryService.delete(userId, named("Moradia").getId());
        categoryRepository.flush();

        Category again = categoryService.create(userId, "Moradia", "#3B82F6", "house", named("Essenciais").getId());

        assertThat(again.getId()).isNotNull();
    }

    @Test
    void colorOrIconOutsideTheListsIsRejected() {
        atStepFive(List.of("housing"));
        UUID id = named("Moradia").getId();
        UUID group = named("Essenciais").getId();

        assertThatThrownBy(() -> categoryService.update(userId, id, null, "#123456", null))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.COLOR_INVALID_MESSAGE);
        assertThatThrownBy(() -> categoryService.update(userId, id, null, null, "skull"))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.ICON_INVALID_MESSAGE);
        assertThatThrownBy(() -> categoryService.create(userId, "X", "red", "house", group))
            .isInstanceOf(BadRequestException.class);
        assertThat(categoryService.update(userId, id, null, "#EC4899", "gift").getIconName()).isEqualTo("gift");
    }

    @Test
    void invalidParentIsRejected() {
        atStepFive(List.of("housing"));
        UUID other = newUser();
        categoryService.createFromSuggestions(other, List.of("leisure"));
        categoryRepository.flush();

        // a child as parent (depth 2), another user's group, an unknown id, no parent
        for (UUID parent : new UUID[] {named("Moradia").getId(), named(other, "Variáveis").getId(), UUID.randomUUID()}) {
            assertThatThrownBy(() -> categoryService.create(userId, "Pets", "#F59E0B", "paw-print", parent))
                .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.PARENT_INVALID_MESSAGE);
        }
        assertThatThrownBy(() -> categoryService.create(userId, "Pets", "#F59E0B", "paw-print", null))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.PARENT_REQUIRED_MESSAGE);
    }

    @Test
    void removedGroupCannotReceiveNewCategories() {
        atStepFive(List.of("housing", "leisure"));
        UUID variable = named("Variáveis").getId();
        categoryService.delete(userId, named("Lazer").getId());
        categoryService.delete(userId, variable);
        categoryRepository.flush();

        assertThatThrownBy(() -> categoryService.create(userId, "Pets", "#F59E0B", "paw-print", variable))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteIsASoftDeleteAndTheLastChildIsKept() {
        atStepFive(List.of("housing", "leisure"));
        UUID housing = named("Moradia").getId();
        UUID leisure = named("Lazer").getId();

        categoryService.delete(userId, housing);
        categoryRepository.flush();

        assertThat(row(housing).get("deleted_at")).isNotNull();
        assertThat(categoryService.listActive(userId)).extracting(Category::getCategoryName).doesNotContain("Moradia");
        assertThatThrownBy(() -> categoryService.delete(userId, leisure))
            .isInstanceOf(ConflictException.class).hasMessage(CategoryService.LAST_CHILD_MESSAGE);
        assertThat(row(leisure).get("deleted_at")).isNull();
    }

    @Test
    void groupWithChildrenCannotBeRemovedButAnEmptyOneCan() {
        atStepFive(List.of("housing", "leisure"));
        UUID essentials = named("Essenciais").getId();

        assertThatThrownBy(() -> categoryService.delete(userId, essentials))
            .isInstanceOf(ConflictException.class).hasMessage(CategoryService.GROUP_NOT_EMPTY_MESSAGE);

        categoryService.delete(userId, named("Moradia").getId());
        categoryService.delete(userId, essentials);
        categoryRepository.flush();

        assertThat(row(essentials).get("deleted_at")).isNotNull();
        assertThat(categoryService.listActive(userId)).extracting(Category::getCategoryName)
            .containsExactly("Lazer", "Variáveis");
    }

    @Test
    void groupsCanBeRenamedButNotToAnotherCategorysName() {
        atStepFive(ALL_KEYS);
        UUID essentials = named("Essenciais").getId();

        assertThat(categoryService.update(userId, essentials, "Básicos", "#64748B", "wallet").getCategoryName())
            .isEqualTo("Básicos");
        assertThatThrownBy(() -> categoryService.update(userId, essentials, "financeiro", null, null))
            .isInstanceOf(ConflictException.class);
        // a group cannot take the name of any child either
        assertThatThrownBy(() -> categoryService.update(userId, essentials, "Moradia", null, null))
            .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> categoryService.create(userId, "básicos", "#3B82F6", "house", named("Variáveis").getId()))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void anotherUsersOrARemovedCategoryIsNotFound() {
        atStepFive(List.of("housing", "food"));
        UUID other = newUser();
        categoryService.createFromSuggestions(other, List.of("leisure", "clothing"));
        categoryRepository.flush();
        UUID theirs = named(other, "Lazer").getId();

        assertThatThrownBy(() -> categoryService.update(userId, theirs, "X", null, null))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> categoryService.delete(userId, theirs))
            .isInstanceOf(ResourceNotFoundException.class);

        UUID housing = named("Moradia").getId();
        categoryService.delete(userId, housing);
        assertThatThrownBy(() -> categoryService.update(userId, housing, "X", null, null))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> categoryService.delete(userId, housing))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(row(theirs)).containsEntry("category_name", "Lazer").containsEntry("deleted_at", null);
    }

    @Test
    void listIsOrderedByName() {
        atStepFive(List.of("transport", "housing", "food"));
        categoryService.create(userId, "Academia", "#F59E0B", "dumbbell", named("Essenciais").getId());
        categoryRepository.flush();

        assertThat(categoryService.listActive(userId)).extracting(Category::getCategoryName)
            .containsExactly("Academia", "Alimentação", "Essenciais", "Moradia", "Transporte");
    }

    @Test
    void invisibleOnlyNameIs400AndEdgeInvisibleCharactersAreStripped() {
        atStepFive(List.of("housing"));
        UUID id = named("Moradia").getId();

        assertThatThrownBy(() -> categoryService.update(userId, id, "\u200B\uFEFF \u200B", null, null))
            .isInstanceOf(BadRequestException.class).hasMessage(CategoryService.NAME_REQUIRED_MESSAGE);
        assertThat(categoryService.update(userId, id, "\uFEFFCasa\u200B", null, null).getCategoryName()).isEqualTo("Casa");
    }

    @Test
    void namesAreStoredInNfcAndAnNfdDuplicateIsAConflict() {
        atStepFive(List.of("housing", "health"));
        UUID group = named("Essenciais").getId();
        String nfd = "Sau\u0301de"; // "Saúde" with a combining acute accent

        assertThatThrownBy(() -> categoryService.create(userId, nfd, "#3B82F6", "house", group))
            .isInstanceOf(ConflictException.class).hasMessage(CategoryService.DUPLICATE_NAME_MESSAGE);

        Category created = categoryService.create(userId, "Cafe\u0301", "#3B82F6", "coffee", group);
        categoryRepository.flush();
        assertThat(row(created.getId())).containsEntry("category_name", "Caf\u00E9");
    }
}
