package dev.rs2multifilter.minecraft.client;

import java.util.function.ToIntFunction;

/** Pixel-based middle elision keeps the start and the identifying suffix. */
public final class EditorText {
    public static String fit(String text, int pixels, ToIntFunction<String> width) {
        if (width.applyAsInt(text) <= pixels) return text;
        if (width.applyAsInt("…") > pixels) return "";
        int count = text.codePointCount(0, text.length());
        int front = (count + 1) / 2, back = count / 2;
        while (front + back > 0) {
            String a = text.substring(0, text.offsetByCodePoints(0, front));
            String b = text.substring(text.offsetByCodePoints(text.length(), -back));
            String result = a + "…" + b;
            if (width.applyAsInt(result) <= pixels) return result;
            if (front > 0 && (back == 0 || width.applyAsInt(a) >= width.applyAsInt(b))) front--;
            else back--;
        }
        return "…";
    }
    private EditorText() {}
}
