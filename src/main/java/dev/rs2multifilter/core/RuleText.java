package dev.rs2multifilter.core;

import java.util.*;

public final class RuleText {
    private RuleText() {}

    public static List<FilterRule> parse(String input) {
        if (input == null) throw new IllegalArgumentException("规则不能为 null");
        List<FilterRule> result = new ArrayList<>();
        int line = 0;
        for (String raw : input.split("\\R", -1)) {
            line++;
            String text = raw.trim();
            if (text.isBlank() || text.startsWith("//")) continue;
            boolean exclude = text.startsWith("-");
            if (!exclude && !text.startsWith("+")) throw new IllegalArgumentException("第 " + line + " 行缺少 + 或 -");
            String body = text.substring(1);
            int colon = body.indexOf(':');
            if (colon <= 0) throw new IllegalArgumentException("第 " + line + " 行缺少规则类型");
            FilterRule.Kind kind;
            try { kind = FilterRule.Kind.valueOf(body.substring(0, colon).toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("第 " + line + " 行未知类型"); }
            try { result.add(new FilterRule(kind, body.substring(colon + 1), exclude)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("第 " + line + " 行: " + e.getMessage()); }
            if (result.size() > 256) throw new IllegalArgumentException("一个分类最多 256 条规则");
        }
        if (result.isEmpty()) throw new IllegalArgumentException("分类至少需要一条规则");
        return List.copyOf(result);
    }

    public static String format(List<FilterRule> rules) {
        StringJoiner sj = new StringJoiner("\n");
        for (var rule : rules) sj.add((rule.exclude() ? "-" : "+") + rule.kind().name().toLowerCase(Locale.ROOT) + ":" + rule.value());
        return sj.toString();
    }
}
