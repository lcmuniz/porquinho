package com.porquinho.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed catalog of the suggested spending categories (Story 3.4), the single source for the onboarding
 * step 4 screen (GET /api/v1/categories/suggestions) and for the categories created from it.
 * Each group becomes a parent category and each chosen suggestion a child of its group (decision A).
 * Keys are stable identifiers; names, Lucide icon names and #RRGGBB colors come from here.
 */
public final class CategorySuggestions {

    /** A suggested category, created as a child of its group. */
    public record Suggestion(String key, String name, String iconName, String colorHex) { }

    /** A suggestion group, created as a parent category when at least one of its suggestions is chosen. */
    public record Group(String key, String name, String iconName, String colorHex, List<Suggestion> suggestions) { }

    public static final List<Group> GROUPS = List.of(
        new Group("essentials", "Essenciais", "shield-check", "#2563EB", List.of(
            new Suggestion("housing", "Moradia", "house", "#3B82F6"),
            new Suggestion("food", "Alimentação", "utensils", "#F97316"),
            new Suggestion("transport", "Transporte", "car", "#0EA5E9"),
            new Suggestion("health", "Saúde", "heart-pulse", "#EF4444")
        )),
        new Group("variable", "Variáveis", "shuffle", "#9333EA", List.of(
            new Suggestion("leisure", "Lazer", "party-popper", "#EC4899"),
            new Suggestion("education", "Educação", "graduation-cap", "#8B5CF6"),
            new Suggestion("clothing", "Vestuário", "shirt", "#F59E0B"),
            new Suggestion("subscriptions", "Assinaturas", "repeat", "#6366F1")
        )),
        new Group("financial", "Financeiro", "landmark", "#059669", List.of(
            new Suggestion("investments", "Investimentos", "trending-up", "#10B981"),
            new Suggestion("emergency_fund", "Reserva de Emergência", "piggy-bank", "#14B8A6")
        ))
    );

    /** Suggestion key -> its group, in catalog order. */
    private static final Map<String, Group> GROUP_BY_SUGGESTION_KEY = new LinkedHashMap<>();

    static {
        for (Group group : GROUPS) {
            for (Suggestion suggestion : group.suggestions()) {
                GROUP_BY_SUGGESTION_KEY.put(suggestion.key(), group);
            }
        }
    }

    private CategorySuggestions() {
    }

    public static boolean isKnown(String key) {
        return key != null && GROUP_BY_SUGGESTION_KEY.containsKey(key);
    }

    /** Number of suggestions in the catalog (10). */
    public static int size() {
        return GROUP_BY_SUGGESTION_KEY.size();
    }
}
