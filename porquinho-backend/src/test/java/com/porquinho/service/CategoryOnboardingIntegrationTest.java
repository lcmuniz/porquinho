package com.porquinho.service;

import com.porquinho.dto.CreateAccountRequest;
import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.repository.AccountRepository;
import com.porquinho.repository.CategoryRepository;
import com.porquinho.repository.OnboardingProgressRepository;
import com.porquinho.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 3.4: suggested categories, onboarding step 4, against the real schema (PostgreSQL via Testcontainers,
 * Flyway V18): step-4 matrix (all, some, none, unknown key, repeat, steps 1 to 3), groups as parents (decision A),
 * listing scoped by user, purge SQL with the self-referencing FK, FK to users and export.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CategoryOnboardingIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private DataExportService dataExportService;

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

    private void atStepThree() {
        onboardingService.completeTutorial(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
        accountService.create(userId, new CreateAccountRequest("Nubank", null, "CHECKING", BigDecimal.ONE));
        accountRepository.flush();
        onboardingService.completeAccounts(userId, false, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private void atStepFour() {
        atStepThree();
        onboardingService.completeCards(userId, true, "127.0.0.1");
        onboardingProgressRepository.flush();
    }

    private void complete(List<String> keys) {
        onboardingService.completeCategories(userId, keys, "127.0.0.1");
        onboardingProgressRepository.flush();
        categoryRepository.flush();
    }

    private Map<String, Object> progressRow() {
        return jdbc.queryForMap("SELECT current_step, steps_completed FROM onboarding_progress WHERE user_id = ?", userId);
    }

    private List<String> categoriesAudits() {
        return jdbc.queryForList(
            "SELECT metadata FROM audit_logs WHERE user_id = ? AND event_type = 'onboarding_categories_completed'",
            String.class, userId);
    }

    private long categoryRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE user_id = ?", Long.class, userId);
    }

    /** Parent name -> sorted child names, read straight from the table. */
    private Map<String, List<String>> tree() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT p.category_name AS parent, c.category_name AS child FROM categories c "
                + "JOIN categories p ON p.id = c.parent_category_id WHERE c.user_id = ? ORDER BY p.category_name, c.category_name",
            userId);
        return rows.stream().collect(Collectors.groupingBy(r -> (String) r.get("parent"), java.util.TreeMap::new,
            Collectors.mapping(r -> (String) r.get("child"), Collectors.toList())));
    }

    @Test
    void allSuggestionsCreateThreeGroupsAndTenChildrenThenStepFive() {
        atStepFour();

        complete(ALL_KEYS);

        assertThat(categoryRows()).isEqualTo(13);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories WHERE user_id = ? AND parent_category_id IS NULL", Long.class, userId)).isEqualTo(3);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories WHERE user_id = ? AND is_system_default", Long.class, userId)).isEqualTo(13);
        assertThat(tree().keySet()).containsExactly("Essenciais", "Financeiro", "Variáveis");
        assertThat(tree().get("Financeiro")).containsExactly("Investimentos", "Reserva de Emergência");
        assertThat(progressRow()).containsEntry("current_step", 5)
            .containsEntry("steps_completed", "[\"tutorial_viewed\",\"accounts_added\",\"cards_skipped\",\"categories_suggested\"]");
        assertThat(categoriesAudits()).containsExactly("{\"count\":10}");
    }

    @Test
    void uncheckingClothingAndSubscriptionsLeavesThreeParentsAndEightChildren() {
        // Acceptance criterion: unchecks Vestuário and Assinaturas, then Próximo
        atStepFour();
        List<String> keys = ALL_KEYS.stream().filter(k -> !k.equals("clothing") && !k.equals("subscriptions")).toList();

        complete(keys);

        Map<String, List<String>> tree = tree();
        assertThat(tree.get("Essenciais")).containsExactly("Alimentação", "Moradia", "Saúde", "Transporte");
        assertThat(tree.get("Variáveis")).containsExactly("Educação", "Lazer");
        assertThat(tree.get("Financeiro")).containsExactly("Investimentos", "Reserva de Emergência");
        assertThat(categoryRows()).isEqualTo(11);
        assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM categories WHERE user_id = ? AND NOT is_system_default", Long.class, userId)).isZero();
        assertThat(progressRow()).containsEntry("current_step", 5);
        assertThat(categoriesAudits()).containsExactly("{\"count\":8}");
    }

    @Test
    void housingAndLeisureCreateOnlyTheirGroupsWithIconsAndColorsFromTheCatalog() {
        atStepFour();

        complete(List.of("leisure", "housing", "housing"));

        assertThat(tree()).containsOnlyKeys("Essenciais", "Variáveis");
        assertThat(tree().get("Essenciais")).containsExactly("Moradia");
        assertThat(tree().get("Variáveis")).containsExactly("Lazer");
        Map<String, Object> housing = jdbc.queryForMap(
            "SELECT color_hex, icon_name FROM categories WHERE user_id = ? AND category_name = 'Moradia'", userId);
        assertThat(housing).containsEntry("color_hex", "#3B82F6").containsEntry("icon_name", "house");
        Map<String, Object> essentials = jdbc.queryForMap(
            "SELECT color_hex, icon_name FROM categories WHERE user_id = ? AND category_name = 'Essenciais'", userId);
        assertThat(essentials).containsEntry("color_hex", "#2563EB").containsEntry("icon_name", "shield-check");
        assertThat(categoriesAudits()).containsExactly("{\"count\":2}");
    }

    @Test
    void noKeyOrAnUnknownKeyIs400AndCreatesNothing() {
        atStepFour();

        assertThatThrownBy(() -> onboardingService.completeCategories(userId, List.of(), "127.0.0.1"))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> onboardingService.completeCategories(userId, List.of("xyz"), "127.0.0.1"))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> onboardingService.completeCategories(userId, List.of("housing", "xyz"), "127.0.0.1"))
            .isInstanceOf(BadRequestException.class);

        assertThat(categoryRows()).isZero();
        assertThat(progressRow()).containsEntry("current_step", 4);
        assertThat(categoriesAudits()).isEmpty();
    }

    @Test
    void repeatingThePostFromStepFiveCreatesNothingAndWritesNoAudit() {
        atStepFour();
        complete(List.of("housing"));

        complete(ALL_KEYS);

        assertThat(categoryRows()).isEqualTo(2);
        assertThat(progressRow()).containsEntry("current_step", 5);
        assertThat(categoriesAudits()).containsExactly("{\"count\":1}");
    }

    @Test
    void categoriesStepIsAConflictOnStepsOneToThree() {
        onboardingService.getOrCreate(userId);
        assertThatThrownBy(() -> onboardingService.completeCategories(userId, List.of("housing"), "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        atStepThree();
        assertThatThrownBy(() -> onboardingService.completeCategories(userId, List.of("housing"), "127.0.0.1"))
            .isInstanceOf(ConflictException.class);

        assertThat(categoryRows()).isZero();
        assertThat(progressRow()).containsEntry("current_step", 3);
    }

    @Test
    void listReturnsOnlyTheUsersActiveCategories() {
        atStepFour();
        complete(List.of("housing"));
        UUID other = newUser();
        categoryService.createFromSuggestions(other, List.of("leisure"));
        categoryRepository.flush();
        jdbc.update("UPDATE categories SET deleted_at = NOW() WHERE user_id = ? AND category_name = 'Moradia'", userId);

        List<Category> mine = categoryService.listActive(userId);

        assertThat(mine).extracting(Category::getCategoryName).containsExactly("Essenciais");
        assertThat(categoryService.listActive(other)).extracting(Category::getCategoryName)
            .containsExactlyInAnyOrder("Variáveis", "Lazer");
    }

    @Test
    void purgeDeletesParentsAndChildrenInOneStatementAndThenTheUser() {
        // Purge SQL: the self-referencing FK does not block a single DELETE; other users keep theirs
        categoryService.createFromSuggestions(userId, ALL_KEYS);
        categoryRepository.flush();
        UUID other = newUser();
        categoryService.createFromSuggestions(other, List.of("housing"));
        categoryRepository.flush();

        assertThat(categoryRepository.deleteByUserId(userId)).isEqualTo(13);
        assertThat(userRepository.deleteByIdNative(userId)).isEqualTo(1);
        assertThat(categoryService.listActive(other)).hasSize(2);
    }

    @Test
    void foreignKeyBlocksDeletingTheUserWhileCategoriesExist() {
        categoryService.createFromSuggestions(userId, List.of("housing"));
        categoryRepository.flush();

        assertThatThrownBy(() -> userRepository.deleteByIdNative(userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aCategoryCannotBeItsOwnParent() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO categories (id, user_id, category_name, parent_category_id, color_hex, icon_name) "
                + "VALUES (?, ?, 'X', ?, '#3B82F6', 'tag')", id, userId, id))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void colorHexOutsideTheRrggbbFormatIsRejectedByTheDatabase() {
        assertThatThrownBy(() -> jdbc.update(
            "INSERT INTO categories (user_id, category_name, color_hex, icon_name) VALUES (?, 'X', 'red', 'tag')", userId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void exportIncludesEveryCategoryWithItsParent() {
        categoryService.createFromSuggestions(userId, List.of("housing"));
        categoryRepository.flush();

        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) dataExportService.collectUserData(userId).get("categories");

        assertThat(rows).hasSize(2);
        Map<String, Object> parent = rows.stream().filter(r -> r.get("parent_category_id") == null).findFirst().orElseThrow();
        Map<String, Object> child = rows.stream().filter(r -> r.get("parent_category_id") != null).findFirst().orElseThrow();
        assertThat(parent).containsEntry("category_name", "Essenciais").containsEntry("is_system_default", true);
        assertThat(child).containsEntry("category_name", "Moradia").containsEntry("parent_category_id", parent.get("id"))
            .containsEntry("icon_name", "house").containsEntry("deleted_at", null);
    }
}
