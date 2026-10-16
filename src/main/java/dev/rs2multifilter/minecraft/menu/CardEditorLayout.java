package dev.rs2multifilter.minecraft.menu;

import java.util.List;

/** One coordinate system for controls, real inventory, reference and inline rules. */
public final class CardEditorLayout {
    public static final int WIDTH = 310, HEIGHT = 238, CONFIG_HEIGHT = 122;
    public static final Rect INVENTORY = new Rect(8, 126, 176, 108);
    public static final Rect RULE_PANEL = new Rect(190, 126, 112, 108);
    public static final Rect RULE_LIST = new Rect(194, 146, 104, 70);
    public static final int INVENTORY_X = 15, INVENTORY_Y = 144, HOTBAR_Y = 202;
    public static final int REFERENCE_X = 17, REFERENCE_Y = 54;
    public static final Rect PREVIOUS = new Rect(8, 21, 18, 18);
    public static final Rect NAME = new Rect(30, 21, 180, 18);
    public static final Rect NEXT = new Rect(214, 21, 18, 18);
    public static final Rect NEW_CATEGORY = new Rect(238, 21, 30, 18);
    public static final Rect DELETE_CATEGORY = new Rect(272, 21, 30, 18);
    public static final Rect ATTRIBUTE = new Rect(42, 52, 208, 20);
    public static final Rect INCLUDE = new Rect(254, 52, 22, 20);
    public static final Rect EXCLUDE = new Rect(280, 52, 22, 20);
    public static final Rect ANY = new Rect(14, 92, 20, 20);
    public static final Rect ALL = new Rect(36, 92, 20, 20);
    public static final Rect DENY = new Rect(58, 92, 20, 20);
    public static final Rect FUZZY = new Rect(92, 92, 36, 20);
    public static final Rect ICON = new Rect(132, 92, 36, 20);
    public static final Rect CLEAR_RULES = new Rect(210, 92, 38, 20);
    public static final Rect SAVE = new Rect(262, 92, 40, 20);

    public static List<Rect> controls() {
        return List.of(PREVIOUS, NAME, NEXT, NEW_CATEGORY, DELETE_CATEGORY,
            ATTRIBUTE, INCLUDE, EXCLUDE, ANY, ALL, DENY, FUZZY, ICON, CLEAR_RULES, SAVE);
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
