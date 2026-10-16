package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.support.stretching.AbstractStretchingScreen;
import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import dev.rs2multifilter.minecraft.client.GridTabs;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Opaque backgrounds below the real item, hover and carried-stack layers. */
@Mixin(value = AbstractStretchingScreen.class, remap = false)
public abstract class GridPanelBackgroundMixin {
    @Inject(method = "renderBg", at = @At("TAIL"))
    private void rs2mf$panels(GuiGraphics g, float tick, int mouseX, int mouseY, CallbackInfo ci) {
        if ((Object)this instanceof AbstractGridScreen<?> screen) GridTabs.renderAttachedPanels(screen, g);
    }
}
