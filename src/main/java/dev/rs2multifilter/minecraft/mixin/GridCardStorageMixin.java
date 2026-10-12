package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AbstractBaseNetworkNodeContainerBlockEntity.class, remap = false)
public abstract class GridCardStorageMixin implements InstalledCardHolder {
    @Unique private static final String RS2MF_CARDS = "rs2multifilterCards";
    @Unique private static final String RS2MF_OLD_CARD = "rs2multifilterInstalledCard";
    @Unique private final ItemStack[] rs2mf$cards =
        {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    @Override public ItemStack rs2mf$getInstalledCard(int slot) {
        return valid(slot) && (Object)this instanceof AbstractGridBlockEntity
            ? rs2mf$cards[slot] : ItemStack.EMPTY;
    }
    @Override public void rs2mf$setInstalledCard(int slot, ItemStack stack) {
        if (!valid(slot) || !((Object)this instanceof AbstractGridBlockEntity)) return;
        rs2mf$cards[slot] = stack.copy();
        ((AbstractBaseNetworkNodeContainerBlockEntity<?>) (Object)this).setChanged();
    }
    @Unique private static boolean valid(int slot) { return slot >= 0 && slot < SLOT_COUNT; }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void rs2mf$saveCards(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!((Object)this instanceof AbstractGridBlockEntity)) return;
        ListTag list = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!rs2mf$cards[i].isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("slot", (byte)i);
                entry.put("stack", rs2mf$cards[i].save(registries));
                list.add(entry);
            }
        }
        tag.put(RS2MF_CARDS, list);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void rs2mf$loadCards(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!((Object)this instanceof AbstractGridBlockEntity)) return;
        for (int i = 0; i < SLOT_COUNT; i++) rs2mf$cards[i] = ItemStack.EMPTY;
        if (tag.contains(RS2MF_CARDS, 9)) {
            ListTag list = tag.getList(RS2MF_CARDS, 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                int slot = entry.getByte("slot");
                if (valid(slot)) rs2mf$cards[slot] =
                    ItemStack.parseOptional(registries, entry.getCompound("stack"));
            }
        } else if (tag.contains(RS2MF_OLD_CARD, 10)) {
            // Preserve already-installed cards from the 0.6 prototype.
            rs2mf$cards[0] = ItemStack.parseOptional(registries, tag.getCompound(RS2MF_OLD_CARD));
        }
    }
}