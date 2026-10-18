package dev.rs2multifilter.minecraft.network;

import dev.rs2multifilter.core.CardWireCodec;
import dev.rs2multifilter.minecraft.CardComponents;
import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.menu.CardEditorMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SaveCardPayload(int menuId, String data) implements CustomPacketPayload {
    public static final Type<SaveCardPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MultiFilterMod.ID, "save_card"));
    public static final StreamCodec<ByteBuf, SaveCardPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, SaveCardPayload::menuId,
        ByteBufCodecs.STRING_UTF8, SaveCardPayload::data, SaveCardPayload::new
    );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SaveCardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.containerMenu instanceof CardEditorMenu menu)
                    || menu.containerId != payload.menuId() || !menu.stillValid(player)) return;
            var hand = menu.editingCard();
            if (!hand.is(MultiFilterMod.FILTER_CARD.get())) return;
            var original = CardComponents.inspect(hand);
            if (!original.readable()) {
                CardComponents.reportFailure(player, "保存", original.problem());
                return;
            }
            try {
                var card = CardWireCodec.decode(payload.data());
                CardComponents.write(hand, card);
                player.getInventory().setChanged();
                menu.broadcastChanges();
                player.closeContainer();
            } catch (IllegalArgumentException e) {
                CardComponents.reportFailure(player, "保存", e.getMessage());
            }
        });
    }
}
