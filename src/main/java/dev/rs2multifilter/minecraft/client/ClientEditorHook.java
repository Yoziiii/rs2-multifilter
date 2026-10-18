package dev.rs2multifilter.minecraft.client;

import dev.rs2multifilter.minecraft.MultiFilterMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = MultiFilterMod.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientEditorHook {
    private ClientEditorHook() {}
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(MultiFilterMod.CARD_EDITOR_MENU.get(), CardEditorScreen::new);
    }
}
