package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Reuse RS 2.0.9's own panel corners/edges and its opaque 18px slot sprite. */
public final class RsUi {
    public static final ResourceLocation FILTER = id("textures/gui/generic_filter.png");
    public static final ResourceLocation TAB_SELECTED = ResourceLocation.withDefaultNamespace(
        "container/creative_inventory/tab_top_selected_2");
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
    public static void iconTab(GuiGraphics g, int x, int y, boolean selected) {
        ResourceLocation sprite = selected ? TAB_SELECTED : TAB_UNSELECTED;
        int height = selected ? 27 : 23;
        g.blitSprite(sprite, 26, 32, 0, 0, x, y, 26, 7);
        g.blitSprite(sprite, 26, 32, 0, 7, x, y + 7, 26, height - 11);
        g.blitSprite(sprite, 26, 32, 0, 28, x, y + height - 4, 26, 4);
    }
}
