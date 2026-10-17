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
    private static int previewIndex, scaleWaitFrames;

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
        var client = Minecraft.getInstance();
        int scale = previewIndex < 4 ? 2 : 3;
        if (Math.abs(client.getWindow().getGuiScale() - scale) > 0.01) {
            if (++scaleWaitFrames > 30) throw new IllegalStateException("Cannot reach GUI scale " + scale);
            client.options.guiScale().set(scale);
            client.resizeDisplay();
            return;
        }
        scaleWaitFrames = 0;
        vanilla.renderWidget(event.getGuiGraphics(), 0, 0, 0);
        name.renderWidget(event.getGuiGraphics(), 0, 0, 0);
        CreativeTabPreview.capture(event.getGuiGraphics(), scale, previewIndex % 4);
        if (++previewIndex < 8) return;
        passed = true;
        System.out.println("RS2MF_CLIENT_SMOKE_TITLE_READY_EDITBOX_OK");
        System.out.println("RS2MF_CATEGORY_ALIGNMENT_SCALES_OK");
        client.execute(client::stop);
    }
}
