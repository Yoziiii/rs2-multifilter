package dev.rs2multifilter.minecraft.network;

import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.mixin.GridMenuAccessor;
import dev.rs2multifilter.minecraft.storage.InstalledCardHolder;
import com.refinedmods.refinedstorage.common.grid.AbstractGridContainerMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Click physical card button with a filter card on the cursor to install/swap, or empty cursor to remove. */
public record SwapInstalledCardPayload(int menuId) implements CustomPacketPayload {
    public static final Type<SwapInstalledCardPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MultiFilterMod.ID, "swap_installed_card"));
    public static final StreamCodec<ByteBuf, SwapInstalledCardPayload> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.VAR_INT, SwapInstalledCardPayload::menuId,
            SwapInstalledCardPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SwapInstalledCardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof AbstractGridContainerMenu menu)) return;
            if (player.containerMenu.containerId != payload.menuId()) return;
            if (!menu.stillValid(player)) return;
            Object grid = ((GridMenuAccessor) menu).rs2mf$getGrid();
            if (!(grid instanceof InstalledCardHolder holder)) return;

            ItemStack carried = menu.getCarried();
            if (!carried.isEmpty() && !carried.is(MultiFilterMod.FILTER_CARD.get())) return;
            ItemStack installed = holder.rs2mf$getInstalledCard();

            // Only ever exchange the one installed card and the single cursor card.
            holder.rs2mf$setInstalledCard(carried);
            menu.setCarried(installed.isEmpty() ? ItemStack.EMPTY : installed.copy());
            menu.broadcastChanges();
            PacketDistributor.sendToPlayer(player,
                new InstalledCardDataPayload(menu.containerId, cardData(holder.rs2mf$getInstalledCard())));
        });
    }

    public static String cardData(ItemStack installed) {
        if (installed.isEmpty()) return "";
        return dev.rs2multifilter.core.CardWireCodec.encode(dev.rs2multifilter.minecraft.CardComponents.read(installed));
    }
}
