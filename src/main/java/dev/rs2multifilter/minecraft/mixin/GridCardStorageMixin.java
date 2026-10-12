package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.support.network.AbstractBaseNetworkNodeContainerBlockEntity;
import com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The save/load methods are declared on the RS base entity, so the mixin
 * injects there and persists card data only on grid (not unrelated RS nodes).
 */
@Mixin(value = AbstractBaseNetworkNodeContainerBlockEntity.class, remap = false)
public abstract class GridCardStorageMixin implements InstalledCardHolder {
    @Unique private static final String RS2MF_CARD_KEY = "rs2multifilterInstalledCard";
    @Unique private ItemStack rs2mf$installedCard = ItemStack.EMPTY;

    @Override public ItemStack rs2mf$getInstalledCard() {
        return rs2mf$installedCard;
    }
    @Override public void rs2mf$setInstalledCard(ItemStack stack) {
        if (!((Object) this instanceof AbstractGridBlockEntity)) return;
        rs2mf$installedCard = stack.copy();
        ((AbstractBaseNetworkNodeContainerBlockEntity<?>) (Object) this).setChanged();
    }

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void rs2mf$saveCard(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof AbstractGridBlockEntity && !rs2mf$installedCard.isEmpty()) {
            tag.put(RS2MF_CARD_KEY, rs2mf$installedCard.save(registries));
        }
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void rs2mf$loadCard(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        if ((Object) this instanceof AbstractGridBlockEntity) {
            rs2mf$installedCard = tag.contains(RS2MF_CARD_KEY, 10)
                ? ItemStack.parseOptional(registries, tag.getCompound(RS2MF_CARD_KEY))
                : ItemStack.EMPTY;
        }
    }
}
