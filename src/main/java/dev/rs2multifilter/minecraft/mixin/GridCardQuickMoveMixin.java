package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import dev.rs2multifilter.minecraft.storage.InstalledCardSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Installed cards return to real player slots, including on an inactive grid. */
@Mixin(value = AbstractGridContainerMenu.class, remap = false)
public abstract class GridCardQuickMoveMixin extends AbstractContainerMenu {
    protected GridCardQuickMoveMixin(MenuType<?> type, int id) { super(type, id); }

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void rs2mf$returnInstalledCard(Player player, int index,
                                         CallbackInfoReturnable<ItemStack> result) {
        if (index < 0 || index >= slots.size() || !(getSlot(index) instanceof InstalledCardSlot)) return;
        Slot source = getSlot(index);
        if (!source.hasItem() || !source.mayPickup(player)) {
            result.setReturnValue(ItemStack.EMPTY);
            return;
        }
        ItemStack original = source.getItem().copy();
        // Never shrink the holder's stack in place: set() persists and broadcasts
        // the resulting card inventory after the vanilla transfer has succeeded.
        ItemStack remainder = original.copy();
        for (int targetIndex = slots.size() - 1; targetIndex >= 0 && !remainder.isEmpty(); targetIndex--) {
            Slot target = getSlot(targetIndex);
            if (target.container == player.getInventory()
                    && target.getContainerSlot() >= 0 && target.getContainerSlot() < 36) {
                moveItemStackTo(remainder, targetIndex, targetIndex + 1, true);
            }
        }
        if (remainder.getCount() == original.getCount()) {
            // Consume the quick-move even when full. RS must not fall through
            // to its separate "insert player slot into network" path.
            result.setReturnValue(ItemStack.EMPTY);
            return;
        }
        source.set(remainder);
        source.onTake(player, original.copyWithCount(original.getCount() - remainder.getCount()));
        result.setReturnValue(original);
    }
}
