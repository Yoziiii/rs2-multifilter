package dev.rs2multifilter.core;

import java.util.List;
import java.util.Objects;

public record FilterCategory(String name, String iconItemId, List<FilterRule> rules) {
    public FilterCategory {
        Objects.requireNonNull(name);
        Objects.requireNonNull(iconItemId);
        if (name.isBlank() || name.length() > 32) throw new IllegalArgumentException("Category name must be 1..32 characters");
        if (iconItemId.length() > 256) throw new IllegalArgumentException("Icon ID too long");
        rules = List.copyOf(rules);
        if (rules.size() > 256) throw new IllegalArgumentException("Too many rules");
    }

    public boolean matches(FilterRule.ResourceView item) {
        if (rules.isEmpty()) return false;
        boolean include = false;
        boolean includesExist = false;
        for (FilterRule rule : rules) {
            if (rule.exclude() && rule.matches(item)) return false;
            if (!rule.exclude()) {
                includesExist = true;
                if (rule.matches(item)) include = true;
            }
        }
        return !includesExist || include;
    }
}
