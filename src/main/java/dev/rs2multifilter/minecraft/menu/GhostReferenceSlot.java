package dev.rs2multifilter.minecraft.menu;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** A synchronized sample view, never a source of real inventory items. */
public final class GhostReferenceSlot extends Slot {
    public GhostReferenceSlot(Container samples, int x, int y) {
        super(samples, 0, x, y);
    }

    @Override public boolean mayPlace(ItemStack stack) { return false; }
    @Override public boolean mayPickup(Player player) { return false; }
    @Override public int getMaxStackSize() { return 1; }
    @Override public ItemStack remove(int amount) { return ItemStack.EMPTY; }

    @Override public void set(ItemStack stack) {
        super.set(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }
}
