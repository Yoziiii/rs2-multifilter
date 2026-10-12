package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.support.containermenu.AbstractResourceContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla-backed menu slot, integrated with RS resizing. */
@Mixin(value = AbstractResourceContainerMenu.class, remap = false)
public interface ResourceMenuSlotInvoker {
    @Invoker("addSlot") Slot rs2mf$addFilterSlot(Slot slot);
}
