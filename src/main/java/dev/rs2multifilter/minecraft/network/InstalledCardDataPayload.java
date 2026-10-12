package dev.rs2multifilter.minecraft.network;

import dev.rs2multifilter.minecraft.MultiFilterMod;
import dev.rs2multifilter.minecraft.client.ClientCardState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client; empty string means the terminal has no installed card. */
public record InstalledCardDataPayload(int menuId, String encodedCard)
    implements CustomPacketPayload {
    public static final Type<InstalledCardDataPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(MultiFilterMod.ID, "installed_card_data"));
    public static final StreamCodec<ByteBuf, InstalledCardDataPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.VAR_INT, InstalledCardDataPayload::menuId,
            ByteBufCodecs.STRING_UTF8, InstalledCardDataPayload::encodedCard,
            InstalledCardDataPayload::new
        );
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(InstalledCardDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCardState.update(payload.menuId(), payload.encodedCard()));
    }
}
