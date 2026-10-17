package dev.rs2multifilter.minecraft.network;

import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.mixin.GridMenuAccessor;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwapInstalledCardPayload(int menuId, int slot) implements CustomPacketPayload {
    public static final Type<SwapInstalledCardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MultiFilterMod.ID, "swap_installed_card"));
    public static final StreamCodec<ByteBuf, SwapInstalledCardPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SwapInstalledCardPayload::menuId,
            ByteBufCodecs.VAR_INT, SwapInstalledCardPayload::slot,
            SwapInstalledCardPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwapInstalledCardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof AbstractGridContainerMenu menu)) return;
            if (menu.containerId != payload.menuId() || !menu.stillValid(player)) return;
            int slot = payload.slot();
            if (slot < 0 || slot >= InstalledCardHolder.SLOT_COUNT) return;
            Object grid = ((GridMenuAccessor)menu).rs2mf$getGrid();
            if (!(grid instanceof InstalledCardHolder holder)) return;
            ItemStack cursor = menu.getCarried();
            if (!cursor.isEmpty() && (!cursor.is(MultiFilterMod.FILTER_CARD.get()) || cursor.getCount() != 1)) return;
            ItemStack removed = holder.rs2mf$getInstalledCard(slot);
            holder.rs2mf$setInstalledCard(slot, cursor);
            menu.setCarried(removed.isEmpty() ? ItemStack.EMPTY : removed.copy());
            menu.broadcastChanges();
            // Refresh any player already using THIS SAME placed grid.
            for (ServerPlayer viewer : player.server.getPlayerList().getPlayers()) {
                if (!(viewer.containerMenu instanceof AbstractGridContainerMenu viewerMenu)) continue;
                if (!(((GridMenuAccessor)viewerMenu).rs2mf$getGrid() == grid)) continue;
                CardComponents.reportReadStatus(viewer, holder.rs2mf$getInstalledCard(slot), "终端同步");
                PacketDistributor.sendToPlayer(viewer, new InstalledCardDataPayload(
                    viewerMenu.containerId, slot, cardData(holder.rs2mf$getInstalledCard(slot))));
            }
        });
    }

    public static String cardData(ItemStack installed) {
        if (installed.isEmpty()) return "";
        // Server-prepared view only: keep blank items usable by the copy recipe.
        return CardComponents.inspect(installed).viewData();
    }
}
