package dev.rs2multifilter.core;

import java.util.List;
import java.util.Objects;

/** Categories support Create-style OR whitelist, AND whitelist and blacklist. */
public record FilterCategory(String name, String iconItemId, List<FilterRule> rules, Mode mode) {
    public enum Mode { ANY, ALL, DENY }

    public FilterCategory(String name, String iconItemId, List<FilterRule> rules) {
        this(name, iconItemId, rules, Mode.ANY);
    }
    public FilterCategory {
        Objects.requireNonNull(name);
        Objects.requireNonNull(iconItemId);
        Objects.requireNonNull(mode);
        if (name.isBlank() || name.length() > 32) throw new IllegalArgumentException("Category name must be 1..32 characters");
        if (iconItemId.length() > 256) throw new IllegalArgumentException("Icon ID too long");
        rules = List.copyOf(rules);
        if (rules.size() > 256) throw new IllegalArgumentException("Too many rules");
    }

    public boolean matches(FilterRule.ResourceView item) {
        if (rules.isEmpty()) return false;
        boolean hasInclude = false;
        boolean any = false;
        boolean all = true;
        for (FilterRule rule : rules) {
            boolean match = rule.matches(item);
            if (rule.exclude()) {
                if (match) return false;
            } else {
                hasInclude = true;
                any |= match;
                all &= match;
            }
        }
        if (!hasInclude) return true;
        return switch (mode) {
            case ANY -> any;
            case ALL -> all;
            case DENY -> !any;
        };
    }
}