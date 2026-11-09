package com.porquinho.service;

import java.util.List;
import java.util.Locale;

/**
 * Allowed colors and Lucide icon names for categories (Story 3.5), the single source for
 * GET /api/v1/categories/options and for the create/update validation. Every color and icon of the
 * suggestions catalog (CategorySuggestions, Story 3.4) is included, so the suggested categories stay valid.
 * Mantenha em sincronia com o frontend: porquinho-frontend/src/lib/categoryIcons.ts (componente e nome pt-BR de
 * cada ícone e de cada cor) e a lista em porquinho-frontend/src/lib/__tests__/categoryIcons.spec.ts.
 */
public final class CategoryOptions {

    /** Options as returned by GET /api/v1/categories/options. */
    public record Options(List<String> colors, List<String> icons) { }

    /** Palette (#RRGGBB, upper case). The first 13 are the colors of the suggestions catalog. */
    public static final List<String> COLORS = List.of(
        "#2563EB", "#3B82F6", "#0EA5E9", "#14B8A6", "#10B981", "#059669", "#F59E0B",
        "#F97316", "#EF4444", "#EC4899", "#8B5CF6", "#9333EA", "#6366F1", "#64748B"
    );

    /** Lucide icon names (kebab-case). The first 13 are the icons of the suggestions catalog. */
    public static final List<String> ICONS = List.of(
        "shield-check", "house", "utensils", "car", "heart-pulse", "shuffle", "party-popper",
        "graduation-cap", "shirt", "repeat", "landmark", "trending-up", "piggy-bank",
        "shopping-cart", "paw-print", "plane", "gift", "smartphone", "zap", "fuel", "dumbbell",
        "baby", "coffee", "wallet", "folder"
    );

    /** Icon of a category created without one (Story 6.1). */
    public static final String DEFAULT_ICON = "folder";

    public static final Options OPTIONS = new Options(COLORS, ICONS);

    private CategoryOptions() {
    }

    /** Color in the palette, case-insensitive (null is not allowed). */
    public static boolean isAllowedColor(String colorHex) {
        return colorHex != null && COLORS.contains(colorHex.toUpperCase(Locale.ROOT));
    }

    public static boolean isAllowedIcon(String iconName) {
        return iconName != null && ICONS.contains(iconName);
    }

    /** Stored form of an allowed color (upper case, like the palette). */
    public static String normalizeColor(String colorHex) {
        return colorHex == null ? null : colorHex.toUpperCase(Locale.ROOT);
    }
}
