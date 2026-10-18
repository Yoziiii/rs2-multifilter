package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.core.FilterCard;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.logging.Logger;

/** Client preference only. Never writes card components, menus, or server state. */
public final class GridPreferences {
    private static final int MAX_ENTRIES = 64;
    private static final Logger LOGGER = Logger.getLogger(GridPreferences.class.getName());
    private final Path file;
    private final LinkedHashMap<String, Integer> selections = new LinkedHashMap<>();
    private boolean remember = true;

    public GridPreferences(Path file) {
        this.file = file;
        if (!Files.isRegularFile(file)) return;
        try {
            if (Files.size(file) > 32_768) throw new IOException("Preference file too large");
            var values = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { values.load(reader); }
            remember = Boolean.parseBoolean(values.getProperty("remember", "true"));
            for (String key : values.stringPropertyNames()) {
                if (!key.matches("selection\\.[0-9a-f]{64}") || selections.size() >= MAX_ENTRIES) continue;
                try {
                    int index = Integer.parseInt(values.getProperty(key));
                    if (index >= -1 && index < FilterCard.MAX_CATEGORIES) selections.put(key, index);
                } catch (NumberFormatException ignored) { }
            }
            if (!remember) selections.clear();
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.warning("Could not read filter upgrade client preferences; using defaults.");
        }
    }

    public boolean remembersCategory() { return remember; }
    public void setRemember(boolean enabled) {
        remember = enabled;
        if (!enabled) selections.clear();
        save();
    }

    public int restore(String scope, FilterCard card) {
        if (!remember || card.categories().isEmpty()) return -1;
        int index = selections.getOrDefault(key(scope, card), -1);
        return index >= -1 && index < card.categories().size() ? index : -1;
    }

    public void record(String scope, FilterCard card, int index) {
        if (!remember || card.categories().isEmpty() || index < -1 || index >= card.categories().size()) return;
        String key = key(scope, card);
        Integer previous = selections.remove(key);
        selections.put(key, index);
        while (selections.size() > MAX_ENTRIES) selections.remove(selections.keySet().iterator().next());
        if (previous == null || previous != index) save();
    }

    private static String key(String scope, FilterCard card) {
        try {
            // A terminal merges up to FOUR cards. The single-card wire encoder
            // deliberately rejects >64 categories / >16KB, so do not use it here.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hash(digest, scope);
            hash(digest, Integer.toString(card.dataVersion()));
            hash(digest, Integer.toString(card.categories().size()));
            for (var category : card.categories()) {
                hash(digest, category.name());
                hash(digest, category.iconItemId());
                hash(digest, category.mode().name());
                hash(digest, Integer.toString(category.rules().size()));
                for (var rule : category.rules()) {
                    hash(digest, rule.kind().name());
                    hash(digest, Boolean.toString(rule.exclude()));
                    hash(digest, rule.value());
                }
            }
            return "selection." + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static void hash(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private void save() {
        Path temporary = null;
        try {
            Path target = file.toAbsolutePath();
            Files.createDirectories(target.getParent());
            temporary = Files.createTempFile(target.getParent(), "rs2mf-client-", ".tmp");
            var values = new Properties();
            values.setProperty("remember", Boolean.toString(remember));
            selections.forEach((key, index) -> values.setProperty(key, index.toString()));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Filter Upgrade client settings. Controlled by the terminal pin button.");
            }
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) {
            LOGGER.warning("Could not save filter upgrade client preferences; keeping settings for this session.");
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
