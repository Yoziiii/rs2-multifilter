package dev.rs2multifilter.minecraft.menu;

import java.util.List;

/** Shared GUI units: the menu hit boxes and screen artwork use one layout. */
public final class CardEditorLayout {
    public static final int WIDTH = 272, HEIGHT = 228, CONFIG_HEIGHT = 116;
    public static final Rect INVENTORY = new Rect(48, 120, 176, 108);
    public static final int INVENTORY_X = 55, INVENTORY_Y = 138, HOTBAR_Y = 196;
    public static final int REFERENCE_X = 15, REFERENCE_Y = 49;
    public static final Rect PREVIOUS = new Rect(8, 20, 18, 18);
    public static final Rect NAME = new Rect(29, 20, 151, 18);
    public static final Rect NEXT = new Rect(184, 20, 18, 18);
    public static final Rect NEW_CATEGORY = new Rect(206, 20, 26, 18);
    public static final Rect DELETE_CATEGORY = new Rect(236, 20, 28, 18);
    public static final Rect ATTRIBUTE = new Rect(42, 47, 172, 20);
    public static final Rect INCLUDE = new Rect(218, 47, 20, 20);
    public static final Rect EXCLUDE = new Rect(242, 47, 22, 20);
    public static final Rect RULES = new Rect(8, 91, 36, 18);
    public static final Rect ANY = new Rect(48, 91, 22, 18);
    public static final Rect ALL = new Rect(72, 91, 22, 18);
    public static final Rect DENY = new Rect(96, 91, 22, 18);
    public static final Rect FUZZY = new Rect(122, 91, 28, 18);
    public static final Rect ICON = new Rect(154, 91, 30, 18);
    public static final Rect CLEAR_REFERENCE = new Rect(188, 91, 20, 18);
    public static final Rect CLEAR_RULES = new Rect(212, 91, 20, 18);
    public static final Rect SAVE = new Rect(240, 91, 24, 18);

    public static List<Rect> controls() {
        return List.of(PREVIOUS, NAME, NEXT, NEW_CATEGORY, DELETE_CATEGORY,
            ATTRIBUTE, INCLUDE, EXCLUDE, RULES, ANY, ALL, DENY, FUZZY, ICON,
            CLEAR_REFERENCE, CLEAR_RULES, SAVE);
    }

    public record Rect(int x, int y, int width, int height) {
        public boolean contains(double px, double py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
        public boolean intersects(Rect other) {
            return x < other.x + other.width && x + width > other.x
                && y < other.y + other.height && y + height > other.y;
        }
    }

    private CardEditorLayout() {}
}
