package dev.rs2multifilter.core;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record FilterRule(Kind kind, String value, boolean exclude) {
    public enum Kind { MOD, ITEM, TAG, FOOD, DAMAGED, ENCHANTED, CREATE_ATTRIBUTE, FLUID, FLUID_TAG, DAMAGEABLE }

    public FilterRule {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
        value = value.trim().toLowerCase(Locale.ROOT);
        boolean createEncoded = kind == Kind.CREATE_ATTRIBUTE && value.matches("create:[0-9a-f]{2,4096}") && (value.length() % 2 == 1);
        if (!createEncoded && (value.isEmpty() || value.length() > 256 || !value.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?"))) {
            throw new IllegalArgumentException("Invalid registry ID: " + value);
        }
        if ((kind == Kind.ITEM || kind == Kind.TAG || kind == Kind.FLUID || kind == Kind.FLUID_TAG) && !value.contains(":")) {
            throw new IllegalArgumentException("Item/tag requires namespace:path");
        }
        if (kind == Kind.MOD && value.contains(":")) {
            throw new IllegalArgumentException("Mod rule requires namespace only");
        }
    }

    public boolean matches(ResourceView item) {
        return switch (kind) {
            case MOD -> item.id().startsWith(value + ":");
            case ITEM -> item.id().equals(value);
            case TAG -> item.tags().contains(value);
            case FOOD -> item.food();
            case DAMAGED -> item.damaged();
            case ENCHANTED -> item.enchanted();
            case CREATE_ATTRIBUTE, FLUID, FLUID_TAG -> false; // Requires resource-specific game context
            case DAMAGEABLE -> item.damaged();
        };
    }

    public record ResourceView(String id, Set<String> tags, boolean food, boolean damaged, boolean enchanted) {
        public ResourceView(String id, Set<String> tags) { this(id, tags, false, false, false); }
        public ResourceView {
            Objects.requireNonNull(id);
            tags = Set.copyOf(tags);
        }
    }
}
