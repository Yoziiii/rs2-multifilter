package dev.rs2multifilter.core;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record FilterRule(Kind kind, String value, boolean exclude) {
    public enum Kind { MOD, ITEM, TAG }

    public FilterRule {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(value);
        value = value.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.length() > 256 || !value.matches("[a-z0-9_.-]+(:[a-z0-9_./-]+)?")) {
            throw new IllegalArgumentException("Invalid registry ID: " + value);
        }
        if ((kind == Kind.ITEM || kind == Kind.TAG) && !value.contains(":")) {
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
        };
    }

    public record ResourceView(String id, Set<String> tags) {
        public ResourceView {
            Objects.requireNonNull(id);
            tags = Set.copyOf(tags);
        }
    }
}
