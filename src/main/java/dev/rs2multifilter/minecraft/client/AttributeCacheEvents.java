package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.minecraft.MultiFilterMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** World/data lifecycle only: temporary JEI screen changes never invalidate these caches. */
@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT)
public final class AttributeCacheEvents {
    private AttributeCacheEvents() {}
    @SubscribeEvent public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        AttributeCaches.invalidate();
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        AttributeCaches.invalidate();
    }
    @SubscribeEvent public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) AttributeCaches.unload(event.getLevel());
    }
    @SubscribeEvent public static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED)
            AttributeCaches.invalidate();
    }
}
