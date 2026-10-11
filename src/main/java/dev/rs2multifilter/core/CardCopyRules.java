package dev.rs2multifilter.core;

public final class CardCopyRules {
    private CardCopyRules() {}

    public static FilterCard copyConfiguration(FilterCard first, FilterCard second) {
        if (first == null || second == null) return null;
        boolean firstConfigured = !first.categories().isEmpty();
        boolean secondConfigured = !second.categories().isEmpty();
        if (firstConfigured == secondConfigured) return null;
        return (firstConfigured ? first : second).copy();
    }
}
