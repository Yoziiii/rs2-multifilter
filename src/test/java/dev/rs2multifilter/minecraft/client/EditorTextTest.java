package dev.rs2multifilter.minecraft.client;

import org.junit.jupiter.api.Test;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

class EditorTextTest {
    private static final ToIntFunction<String> PIXELS = text -> text.codePoints()
        .map(c -> c > 127 ? 9 : c == 'i' ? 2 : 6).sum();

    @Test void shortTextRemainsExactAndLongTextUsesActualWidth() {
        assertEquals("模糊", EditorText.fit("模糊", 28, PIXELS));
        String full = "标签：#create:handheld_in_deployer_use";
        String shown = EditorText.fit(full, 142, PIXELS);
        assertTrue(PIXELS.applyAsInt(shown) <= 142);
        assertTrue(shown.startsWith("标签"));
        assertTrue(shown.endsWith("use"));
        assertTrue(shown.contains("…"));
        assertEquals("标签：#create:handheld_in_deployer_use", full);
    }

    @Test void tinyWidthsAndUnicodeNeverProduceBrokenSurrogates() {
        assertEquals("", EditorText.fit("完整", 0, PIXELS));
        assertEquals("…", EditorText.fit("完整标签", 9, PIXELS));
        String result = EditorText.fit("样本🛠长长长长长长🛠尾部", 60, PIXELS);
        assertTrue(PIXELS.applyAsInt(result) <= 60);
        for (int i = 0; i < result.length(); i++) {
            if (Character.isHighSurrogate(result.charAt(i)))
                assertTrue(++i < result.length() && Character.isLowSurrogate(result.charAt(i)));
            else assertFalse(Character.isLowSurrogate(result.charAt(i)));
        }
    }
}
