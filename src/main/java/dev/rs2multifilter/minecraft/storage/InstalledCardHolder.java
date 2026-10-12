package dev.rs2multifilter.minecraft.storage;
import net.minecraft.world.item.ItemStack;
/** Exactly four filter-card slots, as in the classic RS grid. */
public interface InstalledCardHolder {
    int SLOT_COUNT = 4;
    ItemStack rs2mf$getInstalledCard(int slot);
    void rs2mf$setInstalledCard(int slot, ItemStack stack);
}