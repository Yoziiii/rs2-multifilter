package dev.rs2multifilter.minecraft.mixin;

import dev.rs2multifilter.minecraft.client.CardEditorScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only the editor's name field loses its text shadow. All native editing stays intact. */
@Mixin(EditBox.class)
public abstract class EditorNameShadowMixin {
    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))
    private int rs2mf$nameText(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color) {
        return (Object)this instanceof CardEditorScreen.NameField
            ? CardEditorScreen.NameField.drawPlain(g, font, text, x, y, color)
            : g.drawString(font, text, x, y, color);
    }

    @Redirect(method = "renderWidget", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I"))
    private int rs2mf$nameCursor(GuiGraphics g, Font font, String text, int x, int y, int color) {
        return (Object)this instanceof CardEditorScreen.NameField
            ? g.drawString(font, text, x, y, color, false) + 1 : g.drawString(font, text, x, y, color);
    }
}
