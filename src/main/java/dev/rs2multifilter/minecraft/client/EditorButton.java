package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compact pixel bevels matching the filter panel, without requiring Create. */
public class EditorButton extends Button {
    private boolean selected;
    private int accent = 0xFF348C56;
    private boolean compact;
    private EditorIcon icon;

    public EditorButton(int x, int y, int width, int height, Component label, OnPress press) {
        super(x, y, width, height, label, press, DEFAULT_NARRATION);
    }

    public void setSelected(boolean selected) { this.selected = selected; }
    public void setAccent(int accent) { this.accent = accent; }
    public void setCompact(boolean compact) { this.compact = compact; }
    public void setIcon(EditorIcon icon) { this.icon = icon; }
    public EditorIcon icon() { return icon; }

    public static void frame(GuiGraphics g, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF24211F);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFFF1EEE5);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFFF1EEE5);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF565550);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF565550);
    }

    @Override protected void renderWidget(GuiGraphics g, int mx, int my, float tick) {
        int color = !active ? 0xFFB4B4B0 : selected ? accent
            : isHoveredOrFocused() ? 0xFFDADADA : 0xFFC6C6C6;
        if (compact) g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
            isHoveredOrFocused() ? 0xFFDFE5E5 : 0xFFD0D0CC);
        else {
            RsUi.panel(g, getX(), getY(), getWidth(), getHeight());
            g.fill(getX() + 4, getY() + 4, getX() + getWidth() - 4, getY() + getHeight() - 4, color);
        }
        if (icon != null) {
            icon.render(g, getX() + getWidth() / 2, getY() + getHeight() / 2,
                !active ? 0xFF92928E : selected ? 0xFFF4F4EE : 0xFF454A4C);
            return;
        }
        var font = Minecraft.getInstance().font;
        String label = EditorText.fit(getMessage().getString(), getWidth() - 6, font::width);
        if (compact) g.drawString(font, label, getX() + 3, getY() + (getHeight() - 8) / 2,
            accent == 0xFF348C56 ? 0xFF365B3D : 0xFF85502D, false);
        else g.drawString(font, label, getX() + (getWidth() - font.width(label)) / 2,
            getY() + (getHeight() - 8) / 2,
            !active ? 0xFF858580 : selected ? 0xFFF4F0E8 : 0xFF404648, false);
    }
}
