package com.porquinho.service;

import com.porquinho.entity.Category;
import com.porquinho.exception.BadRequestException;
import com.porquinho.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Categories (Story 3.4: list and create from the suggestions; editing and custom categories come in
 * Story 3.5 and Epic 6). The user id always comes from the JWT.
 */
@Service
public class CategoryService {

    public static final String EMPTY_KEYS_MESSAGE = "Escolha ao menos uma categoria";
    public static final String UNKNOWN_KEY_MESSAGE =
        "Categoria sugerida desconhecida. Recarregue a página e tente novamente.";

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** The user's active categories, oldest first. */
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
}
