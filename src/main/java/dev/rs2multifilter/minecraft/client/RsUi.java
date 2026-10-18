package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Reuse RS 2.0.9's own panel corners/edges and its opaque 18px slot sprite. */
public final class RsUi {
    public static final ResourceLocation FILTER = id("textures/gui/generic_filter.png");
    public static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/tab_top_selected_2");
    public static final ResourceLocation TAB_SELECTED_LEFT = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/tab_top_selected_1");
    public static final ResourceLocation TAB_SELECTED_RIGHT = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/tab_top_selected_7");
    public static final ResourceLocation TAB_UNSELECTED = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/tab_top_unselected_2");
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("refinedstorage", path); }
    private RsUi() {}

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0xFFC6C6C6);
        // Nine-slice the main 176x137 panel. Corners stay at native pixel size.
        part(g, x, y, 4, 4, 0, 0, 4, 4);
        part(g, x + w - 4, y, 4, 4, 172, 0, 4, 4);
        part(g, x, y + h - 4, 4, 4, 0, 133, 4, 4);
        part(g, x + w - 4, y + h - 4, 4, 4, 172, 133, 4, 4);
        part(g, x + 4, y, w - 8, 4, 4, 0, 168, 4);
        part(g, x + 4, y + h - 4, w - 8, 4, 4, 133, 168, 4);
        part(g, x, y + 4, 4, h - 8, 0, 4, 4, 129);
        part(g, x + w - 4, y + 4, 4, h - 8, 172, 4, 4, 129);
    }
    private static void part(GuiGraphics g, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        g.blit(FILTER, x, y, w, h, (float)u, (float)v, sw, sh, 256, 256);
    }
    public static void slot(GuiGraphics g, int x, int y) {
        g.blitSprite(id("slot"), x - 1, y - 1, 18, 18);
    }
    /** Vanilla creative tabs, compacted vertically by skipping plain middle rows.
     * Keep native 26px artwork; callers own the tab's position and spacing.
     * Selected extends four pixels into the panel; unselected stops above it. */
    public static void iconTab(GuiGraphics g, int x, int y, boolean selected, int terminalLeft) {
        iconTab(g, x, y, selected, terminalLeft, 26);
    }
    public static void iconTab(GuiGraphics g, int x, int y, boolean selected, int terminalLeft, int width) {
        // Only the terminal-edge tab is 25px. Keep the native outer three pixels,
        // skipping one flat interior column, rather than scaling its bevel or icon.
        if (width == 25 && x + width == terminalLeft + 193) {
            ResourceLocation sprite = selected ? TAB_SELECTED_RIGHT : TAB_UNSELECTED;
            int height = selected ? 27 : 23;
            rightTabPart(g, sprite, x, y, 0, 7);
            rightTabPart(g, sprite, x, y + 7, 7, height - 11);
            rightTabPart(g, sprite, x, y + height - 4, 28, 4);
            return;
        }
        ResourceLocation sprite = selected ? (x == terminalLeft ? TAB_SELECTED_LEFT : TAB_SELECTED) : TAB_UNSELECTED;
        int height = selected ? 27 : 23;
        g.blitSprite(sprite, 26, 32, 0, 0, x, y, 26, 7);
        g.blitSprite(sprite, 26, 32, 0, 7, x, y + 7, 26, height - 11);
        if (!selected) {
            g.blitSprite(sprite, 26, 32, 0, 28, x, y + height - 4, 26, 4);
            return;
        }
        // Open the base only over a panel, never over its outer right border or
        // the gap between panels. RS's native corners remain underneath intact.
        // The first creative-tab sprite keeps the terminal's left vertical edge.
        tabConnection(g, sprite, x, y + 23, terminalLeft, terminalLeft + 189);
        tabConnection(g, sprite, x, y + 23, terminalLeft + 201, terminalLeft + 223);
    }
    private static void rightTabPart(GuiGraphics g, ResourceLocation sprite, int x, int y, int v, int height) {
        g.blitSprite(sprite, 26, 32, 0, v, x, y, 22, height);
        g.blitSprite(sprite, 26, 32, 23, v, x + 22, y, 3, height);
    }
    private static void tabConnection(GuiGraphics g, ResourceLocation sprite, int x, int y, int left, int right) {
        int start = Math.max(x, left), end = Math.min(x + 26, right);
        if (start < end) g.blitSprite(sprite, 26, 32, start - x, 28, start, y, end - start, 4);
    }
}
