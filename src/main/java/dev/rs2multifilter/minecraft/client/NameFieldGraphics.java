package dev.rs2multifilter.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** Local drawing adapter for NameField, never installed into vanilla or other mods. */
final class NameFieldGraphics extends GuiGraphics {
    private final GuiGraphics target;

    NameFieldGraphics(GuiGraphics target) {
        super(Minecraft.getInstance(), target.bufferSource());
        this.target = target;
    }

    @Override public int drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        return CardEditorScreen.NameField.drawPlain(target, font, text, x, y, color);
    }

    @Override public int drawString(Font font, String text, int x, int y, int color) {
        return target.drawString(font, text, x, y, color, false) + 1;
    }

    @Override public int drawString(Font font, Component text, int x, int y, int color) {
        return drawString(font, text.getVisualOrderText(), x, y, color);
    }

    // EditBox's border, cursor and selection use the original graphics context.
    @Override public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height) {
        target.blitSprite(sprite, x, y, width, height);
    }
    @Override public void fill(int x1, int y1, int x2, int y2, int color) {
        target.fill(x1, y1, x2, y2, color);
    }
    @Override public void fill(RenderType type, int x1, int y1, int x2, int y2, int color) {
        target.fill(type, x1, y1, x2, y2, color);
    }
    @Override public PoseStack pose() { return target.pose(); }
    @Override public void enableScissor(int x1, int y1, int x2, int y2) { target.enableScissor(x1, y1, x2, y2); }
    @Override public void disableScissor() { target.disableScissor(); }
}
