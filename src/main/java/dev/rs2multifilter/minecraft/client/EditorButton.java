package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compact pixel bevels matching the filter panel, without requiring Create. */
public class EditorButton extends Button {
    private boolean selected;

    public EditorButton(int x, int y, int width, int height, Component label, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
    }

    public void setSelected(boolean selected) { this.selected = selected; }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF24211F);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFF1EEE5);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFF1EEE5);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF565550);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF565550);
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int color = !active ? 0xFF686965 : selected ? 0xFF348C56
            : isHoveredOrFocused() ? 0xFFAAA9A1 : 0xFF898A87;
        frame(g, getX(), getY(), getWidth(), getHeight(), color);
        var font = Minecraft.getInstance().font;
        String label = EditorText.fit(getMessage().getString(), getWidth() - 6, font::width);
        g.drawCenteredString(font, label, getX() + getWidth() / 2,
            getY() + (getHeight() - 8) / 2, active ? 0xFFF4F0E8 : 0xFFA8A8A0);
    }
}
