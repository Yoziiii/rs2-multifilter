package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.grid.screen.AbstractGridScreen;
import dev.rs2multifilter.minecraft.storage.InstalledCardSlot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The legacy slots extend beyond RS2's base imageWidth. Vanilla otherwise
 * treats clicks there as OUTSIDE the container, dropping the carried stack.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class GridSlotOutsideClickMixin {
    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true)
    private void rs2mf$keepFilterClickInside(double mouseX, double mouseY,
            int guiLeft, int guiTop, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object)this instanceof AbstractGridScreen<?> gridScreen)) return;
        for (var slot : gridScreen.getMenu().slots) {
            if (!(slot instanceof InstalledCardSlot)) continue;
            double left = gridScreen.getGuiLeft() + slot.x;
            double top = gridScreen.getGuiTop() + slot.y;
            if (mouseX >= left - 2 && mouseX < left + 18
                && mouseY >= top - 2 && mouseY < top + 18) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
