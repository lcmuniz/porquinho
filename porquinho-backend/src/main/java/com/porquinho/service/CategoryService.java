package com.porquinho.service;

import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.exception.ConflictException;
import com.porquinho.exception.ResourceNotFoundException;
import com.porquinho.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Categories (Story 3.4: list and create from the suggestions; Story 3.5: create custom children, edit name,
 * color and icon, soft delete; moving and creating groups come in Epic 6). The user id always comes from the JWT;
 * another user's category behaves as if it did not exist (404).
 *
 * Hierarchy has one level: a group has no parent and a child's parent is an active group of the same user.
 * Names are unique (case-insensitive) among all the user's active categories, groups and children alike.
 */
@Service
public class CategoryService {

    public static final String EMPTY_KEYS_MESSAGE = "Escolha ao menos uma categoria";
    public static final String UNKNOWN_KEY_MESSAGE =
        "Categoria sugerida desconhecida. Recarregue a página e tente novamente.";

    public static final String NAME_REQUIRED_MESSAGE = "Informe o nome da categoria";
    public static final String NAME_TOO_LONG_MESSAGE = "Use no máximo 50 caracteres no nome da categoria";
    public static final String COLOR_INVALID_MESSAGE = "Escolha uma cor da paleta";
    public static final String ICON_INVALID_MESSAGE = "Escolha um ícone da lista";
    public static final String PARENT_REQUIRED_MESSAGE = "Escolha o grupo da categoria";
    public static final String PARENT_INVALID_MESSAGE = "Grupo inválido. Recarregue a página e tente novamente.";
    public static final String NOTHING_TO_UPDATE_MESSAGE = "Informe o nome, a cor ou o ícone para alterar";
    public static final String DUPLICATE_NAME_MESSAGE = "Já existe uma categoria com esse nome";
    public static final String LAST_CHILD_MESSAGE = "Mantenha ao menos uma categoria";
    public static final String GROUP_NOT_EMPTY_MESSAGE = "Exclua ou mova as categorias deste grupo antes";
    public static final String NOT_FOUND_MESSAGE = "Categoria não encontrada";

    private static final int NAME_MAX_LENGTH = 50;

    /**
     * Leading/trailing whitespace, Unicode spaces (NBSP, which trim()/strip() keep) and invisible format characters
     * (\\p{Cf}: zero-width space U+200B, BOM U+FEFF...).
     */
    private static final Pattern EDGE_SPACES = Pattern.compile("^[\\s\\p{Z}\\p{Cf}]+|[\\s\\p{Z}\\p{Cf}]+$");

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** The user's active categories (groups and children), by name then id. */
    @Transactional(readOnly = true)
    public List<Category> listActive(UUID userId) {
        return categoryRepository.findActiveByUserId(userId);
    }

    /**
     * Distinct chosen keys in the order received. 400 when empty or when any key is not in the catalog.
     */
    public static Set<String> validateKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw new BadRequestException(EMPTY_KEYS_MESSAGE);
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String key : keys) {
            if (!CategorySuggestions.isKnown(key)) {
                throw new BadRequestException(UNKNOWN_KEY_MESSAGE);
            }
            distinct.add(key);
        }
        return distinct;
    }

    /**
     * Creates the chosen suggestions as children of their group (decision A): a group becomes a parent
     * category only when at least one of its suggestions was chosen. Everything is created with
     * is_system_default=true, in catalog order. Returns the number of children created.
     */
    @Transactional
    public int createFromSuggestions(UUID userId, Collection<String> keys) {
        Set<String> chosen = validateKeys(keys);
        Map<String, Category> parents = new LinkedHashMap<>();
        List<Category> children = new ArrayList<>();
        for (CategorySuggestions.Group group : CategorySuggestions.GROUPS) {
            for (CategorySuggestions.Suggestion suggestion : group.suggestions()) {
                if (!chosen.contains(suggestion.key())) {
                    continue;
                }
                Category parent = parents.computeIfAbsent(group.key(), k -> categoryRepository.save(
                    new Category(userId, group.name(), null, group.colorHex(), group.iconName(), true)));
                children.add(new Category(userId, suggestion.name(), parent.getId(),
                    suggestion.colorHex(), suggestion.iconName(), true));
            }
        }
        categoryRepository.saveAll(children);
        return children.size();
    }

    /**
     * Name in Unicode NFC (so "é" typed as e + combining accent equals the precomposed "é") without leading/trailing
     * whitespace or invisible characters; null stays null.
     */
    public static String cleanName(String name) {
        return name == null ? null : EDGE_SPACES.matcher(Normalizer.normalize(name, Normalizer.Form.NFC)).replaceAll("");
    }

    /*
     * Mutations (create, update, delete) start by locking every active category of the user (SELECT ... FOR UPDATE),
     * so concurrent requests of the same user are serialized and the rules below (unique names, at least one child,
     * no group removal with children) are checked on a list no one else can change meanwhile. A waiting request
     * re-reads the rows after the first one commits (PostgreSQL READ COMMITTED), so it sees the removals.
     */

    /**
     * Creates a custom child category (is_system_default=false) inside one of the user's active groups.
     * 400 when the parent is missing, not the user's, removed or itself a child (max depth 1), or when the
     * name, color or icon is invalid; 409 when another active category or group already has the name.
     * Color and icon are optional (Story 6.1): null or blank = the group's color and CategoryOptions.DEFAULT_ICON.
     */
    @Transactional
    public Category create(UUID userId, String name, String colorHex, String iconName, UUID parentId) {
        String cleanName = validName(name);
        boolean hasColor = colorHex != null && !colorHex.isBlank();
        boolean hasIcon = iconName != null && !iconName.isBlank();
        if (hasColor) {
            validateColor(colorHex);
        }
        if (hasIcon) {
            validateIcon(iconName);
        }
        if (parentId == null) {
            throw new BadRequestException(PARENT_REQUIRED_MESSAGE);
        }
        List<Category> active = categoryRepository.findActiveByUserIdForUpdate(userId);
        Category parent = active.stream()
            .filter(c -> c.getId().equals(parentId) && c.getParentCategoryId() == null)
            .findFirst()
            .orElseThrow(() -> new BadRequestException(PARENT_INVALID_MESSAGE));
        ensureUniqueName(active, cleanName, null);
        return categoryRepository.save(new Category(userId, cleanName, parent.getId(),
            hasColor ? CategoryOptions.normalizeColor(colorHex) : parent.getColorHex(),
            hasIcon ? iconName : CategoryOptions.DEFAULT_ICON, false));
    }

    /**
     * Changes any combination of name, color and icon (null = unchanged) of a group or a child; the parent never
     * changes. 404 when the category is not the user's active one; 400 when a value is invalid; 409 when the new
     * name is already used by another active category or group of the user.
     */
    @Transactional
    public Category update(UUID userId, UUID categoryId, String name, String colorHex, String iconName) {
        if (name == null && colorHex == null && iconName == null) {
            throw new BadRequestException(NOTHING_TO_UPDATE_MESSAGE);
        }
        String cleanName = name == null ? null : validName(name);
        if (colorHex != null) {
            validateColor(colorHex);
        }
        if (iconName != null) {
            validateIcon(iconName);
        }
        List<Category> active = categoryRepository.findActiveByUserIdForUpdate(userId);
        Category category = find(active, categoryId);
        if (cleanName != null) {
            ensureUniqueName(active, cleanName, category.getId());
            category.setCategoryName(cleanName);
        }
        if (colorHex != null) {
            category.setColorHex(CategoryOptions.normalizeColor(colorHex));
        }
        if (iconName != null) {
            category.setIconName(iconName);
        }
        return categoryRepository.save(category);
    }

    /**
     * Soft delete (no cascade). A group can only be removed when it has no active children (409); removing an
     * empty group does not count toward the minimum. A child cannot be removed when it is the user's last active
     * child (409): the budget needs at least one envelope. 404 when the category is not the user's active one.
     */
    @Transactional
    public void delete(UUID userId, UUID categoryId) {
        List<Category> active = categoryRepository.findActiveByUserIdForUpdate(userId);
        Category category = find(active, categoryId);
        if (category.getParentCategoryId() == null) {
            if (active.stream().anyMatch(c -> category.getId().equals(c.getParentCategoryId()))) {
                throw new ConflictException(GROUP_NOT_EMPTY_MESSAGE);
            }
        } else if (active.stream().filter(c -> c.getParentCategoryId() != null).count() <= 1) {
            throw new ConflictException(LAST_CHILD_MESSAGE);
        }
        category.setDeletedAt(OffsetDateTime.now());
        categoryRepository.save(category);
    }

    private static Category find(List<Category> active, UUID categoryId) {
        return active.stream()
            .filter(c -> c.getId().equals(categoryId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /** Case-insensitive uniqueness among the active categories with the same parent (null = among the groups). */
    private static void ensureUniqueName(List<Category> active, String name, UUID ignoredId) {
        boolean taken = active.stream()
            .filter(c -> !c.getId().equals(ignoredId))
            .anyMatch(c -> cleanName(c.getCategoryName()).equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException(DUPLICATE_NAME_MESSAGE);
        }
    }

    private static String validName(String name) {
        String clean = cleanName(name);
        if (clean == null || clean.isEmpty()) {
            throw new BadRequestException(NAME_REQUIRED_MESSAGE);
        }
        if (clean.length() > NAME_MAX_LENGTH) {
            throw new BadRequestException(NAME_TOO_LONG_MESSAGE);
        }
        return clean;
    }

    private static void validateColor(String colorHex) {
        if (!CategoryOptions.isAllowedColor(colorHex)) {
            throw new BadRequestException(COLOR_INVALID_MESSAGE);
        }
    }

    private static void validateIcon(String iconName) {
        if (!CategoryOptions.isAllowedIcon(iconName)) {
            throw new BadRequestException(ICON_INVALID_MESSAGE);
        }
    }
}
