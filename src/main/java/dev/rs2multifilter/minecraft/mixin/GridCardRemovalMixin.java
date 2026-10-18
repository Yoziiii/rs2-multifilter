package dev.rs2multifilter.minecraft.mixin;

import com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity;
import com.refinedmods.refinedstorage.common.support.AbstractBaseBlock;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drop cards at RS's actual removal boundary, never from a cancelable break intent.
 * RS wrench dismantling saves to its item and removes the BE first, so it owns that path. */
@Mixin(value = AbstractBaseBlock.class, remap = false)
public abstract class GridCardRemovalMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void rs2mf$dropRemovedCards(BlockState state, Level level, BlockPos pos,
                                       BlockState newState, boolean moved, CallbackInfo ci) {
        if (!(level instanceof ServerLevel world) || world.restoringBlockSnapshots) return;
        // Match RS's own removal guard: rotation, active state and color changes retain their BE.
        if (state.getBlock() == newState.getBlock()
                || state.getBlock().getClass().equals(newState.getBlock().getClass())) return;
        var blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof AbstractGridBlockEntity)
                || !(blockEntity instanceof InstalledCardHolder holder)) return;
        for (int slot = 0; slot < InstalledCardHolder.SLOT_COUNT; slot++) {
            ItemStack installed = holder.rs2mf$getInstalledCard(slot);
            if (installed.isEmpty()) continue;
            ItemStack drop = installed.copy();
            // Claim each stack before publishing it, including reentrant removal during entity spawn.
            holder.rs2mf$setInstalledCard(slot, ItemStack.EMPTY);
            Block.popResource(world, pos, drop);
        }
    }
}
