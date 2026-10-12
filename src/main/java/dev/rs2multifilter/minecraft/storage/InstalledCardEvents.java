package dev.rs2multifilter.minecraft.storage;

import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.mixin.GridMenuAccessor;
import dev.rs2multifilter.minecraft.network.InstalledCardDataPayload;
import dev.rs2multifilter.minecraft.network.SwapInstalledCardPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = MultiFilterMod.ID)
public final class InstalledCardEvents {
    private InstalledCardEvents() {}

    @SubscribeEvent
    public static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getContainer() instanceof AbstractGridContainerMenu menu)) return;
        Object grid = ((GridMenuAccessor) menu).rs2mf$getGrid();
        for (int slot = 0; slot < InstalledCardHolder.SLOT_COUNT; slot++) {
            ItemStack card = grid instanceof InstalledCardHolder holder
                ? holder.rs2mf$getInstalledCard(slot) : ItemStack.EMPTY;
            PacketDistributor.sendToPlayer(player, new InstalledCardDataPayload(
                menu.containerId, slot, SwapInstalledCardPayload.cardData(card)));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel world)) return;
        var blockEntity = world.getBlockEntity(event.getPos());
        if (!(blockEntity instanceof com.refinedmods.refinedstorage.common.grid.AbstractGridBlockEntity)) return;
        if (!(blockEntity instanceof InstalledCardHolder holder) || event.isCanceled()) return;
        for (int slot = 0; slot < InstalledCardHolder.SLOT_COUNT; slot++) {
            ItemStack installed = holder.rs2mf$getInstalledCard(slot);
            if (!installed.isEmpty()) {
                Block.popResource(world, event.getPos(), installed.copy());
                holder.rs2mf$setInstalledCard(slot, ItemStack.EMPTY);
            }
        }
    }
}