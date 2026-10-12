package dev.rs2multifilter.minecraft.storage;

import dev.rs2multifilter.minecraft.MultiFilterMod;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

/**
 * A real, vanilla-synchronized item slot. Server mutations update the grid's
 * persistent card inventory; the client receives the normal menu slot packets.
 */
public final class InstalledCardSlot extends Slot {
    private final Supplier<Object> gridSupplier;
    private final int filterIndex;

    public InstalledCardSlot(SimpleContainer clientInventory, Supplier<Object> gridSupplier,
                             int index, int x, int y) {
        super(clientInventory, index, x, y);
        this.gridSupplier = gridSupplier;
        this.filterIndex = index;
    }

    public int filterIndex() { return filterIndex; }

    private InstalledCardHolder owner() {
        Object grid = gridSupplier.get();
        return grid instanceof InstalledCardHolder holder ? holder : null;
    }

    @Override public ItemStack getItem() {
        InstalledCardHolder owner = owner();
        return owner != null ? owner.rs2mf$getInstalledCard(filterIndex) : super.getItem();
    }

    @Override public void set(ItemStack stack) {
        if (!stack.isEmpty() && (!stack.is(MultiFilterMod.FILTER_CARD.get()) || stack.getCount() != 1)) return;
        InstalledCardHolder holder = owner();
        if (holder != null) holder.rs2mf$setInstalledCard(filterIndex, stack);
        super.set(stack);
    }

    @Override public ItemStack remove(int count) {
        ItemStack current = getItem();
        if (current.isEmpty() || count <= 0) return ItemStack.EMPTY;
        ItemStack taken = current.copyWithCount(Math.min(count, current.getCount()));
        ItemStack rest = current.copy();
        rest.shrink(count);
        set(rest);
        return taken;
    }

    @Override public boolean mayPlace(ItemStack stack) {
        return stack.is(MultiFilterMod.FILTER_CARD.get()) && stack.getCount() == 1;
    }

    @Override public boolean mayPickup(Player player) { return true; }
    @Override public int getMaxStackSize() { return 1; }
}
