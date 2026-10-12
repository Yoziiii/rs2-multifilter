package dev.rs2multifilter.minecraft.storage;

import net.minecraft.world.item.ItemStack;

/** Server-authoritative card storage attached to a placed Refined Storage grid. */
public interface InstalledCardHolder {
    ItemStack rs2mf$getInstalledCard();
    void rs2mf$setInstalledCard(ItemStack stack);
}
