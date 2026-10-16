package dev.rs2multifilter.smoke;

import dev.rs2multifilter.minecraft.client.CardEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Included only by -PclientSmoke=true, never shipped in the mod JAR. */
@EventBusSubscriber(modid = "rs2multifilter", value = Dist.CLIENT)
public final class ClientStartupProbe {
    private static boolean passed;
    private static EditBox vanilla;
    private static CardEditorScreen.NameField name;

    @SubscribeEvent
    public static void titleReady(ScreenEvent.Init.Post event) {
        if (passed || !(event.getScreen() instanceof TitleScreen)) return;
        var client = Minecraft.getInstance();
        vanilla = new EditBox(client.font, 0, 0, 100, 9, Component.literal("vanilla"));
        name = new CardEditorScreen.NameField(client.font, 0, 12, 100, 9, Component.literal("name"));
        vanilla.setValue("vanilla");
        name.setValue("category");
        name.setCursorPosition(3);
        name.setHighlightPos(6);
        if (!vanilla.getValue().equals("vanilla") || !name.getHighlighted().equals("ego")
                || !vanilla.getTextShadow() || name.getTextShadow())
            throw new IllegalStateException("Name field startup regression");
    }

    @SubscribeEvent
    public static void titleRendered(ScreenEvent.Render.Post event) {
        if (passed || name == null || !(event.getScreen() instanceof TitleScreen)) return;
        vanilla.renderWidget(event.getGuiGraphics(), 0, 0, 0);
        name.renderWidget(event.getGuiGraphics(), 0, 0, 0);
        passed = true;
        System.out.println("RS2MF_CLIENT_SMOKE_TITLE_READY_EDITBOX_OK");
        var client = Minecraft.getInstance();
        client.execute(client::stop);
    }
}
