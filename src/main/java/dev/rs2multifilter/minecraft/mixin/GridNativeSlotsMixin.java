package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import dev.rs2multifilter.minecraft.storage.InstalledCardSlot;
import net.minecraft.world.SimpleContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * RS clears and recreates all vanilla menu slots on every grid resize.
 * Reinsert the four true inventory slots AFTER the native player inventory.
 */
@Mixin(value = AbstractGridContainerMenu.class, remap = false)
public abstract class GridNativeSlotsMixin {
    @Unique private final SimpleContainer rs2mf$clientSlotStorage = new SimpleContainer(InstalledCardHolder.SLOT_COUNT);

    @Inject(method = "resized", at = @At("TAIL"))
    private void rs2mf$attachLegacySlots(int width, int top, int rows, CallbackInfo ci) {
        AbstractGridContainerMenu menu = (AbstractGridContainerMenu)(Object)this;
        for (int index = 0; index < InstalledCardHolder.SLOT_COUNT; index++) {
            ((ResourceMenuSlotInvoker)menu).rs2mf$addFilterSlot(
                new InstalledCardSlot(rs2mf$clientSlotStorage,
                    () -> ((GridMenuAccessor)menu).rs2mf$getGrid(),
                    index, 204, 6 + 18 * index));
        }
    }
}
