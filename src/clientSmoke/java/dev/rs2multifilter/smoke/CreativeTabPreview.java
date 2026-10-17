package dev.rs2multifilter.smoke;

import dev.rs2multifilter.minecraft.client.CategoryNavigation;
import dev.rs2multifilter.minecraft.client.RsUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.stream.IntStream;

/** CI-only render samples: actual tab widgets, not a simulated terminal session. */
final class CreativeTabPreview {
    static void capture(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        draw(graphics, 10, 70, 2, 0, 0);
        draw(graphics, 213, 70, 12, 1, 5);
        draw(graphics, 213, 155, 12, 2, 10);
        graphics.pose().popPose();
        graphics.flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            image.writeToFile(Path.of("creative-tab-preview.png"));
        } catch (Exception error) {
            throw new IllegalStateException("Unable to capture tab render regression sample", error);
        }
        System.out.println("RS2MF_CREATIVE_TAB_RENDER_OK");
    }

    private static void draw(GuiGraphics graphics, int x, int terminalY, int count, int page, int selected) {
        var names = IntStream.range(0, count).mapToObj(i -> "C" + i).toList();
        var navigation = new CategoryNavigation(names, String::length, 193);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, count + " categories / page " + (page + 1), x, terminalY - 36, 0xFFFFFF, false);
        RsUi.panel(graphics, x, terminalY, 193, 36);
        graphics.drawString(font, "RS panel edge", x + 6, terminalY + 9, 0x404648, false);
        try {
            var type = Class.forName("dev.rs2multifilter.minecraft.client.GridTabs$TabButton");
            var constructor = type.getDeclaredConstructor(int.class, int.class, Button.OnPress.class);
            constructor.setAccessible(true);
            var icon = type.getDeclaredField("icon");
            var chosen = type.getDeclaredField("chosen");
            icon.setAccessible(true);
            chosen.setAccessible(true);
            for (var tab : navigation.tabs(page)) {
                var button = (Button) constructor.newInstance(x + tab.x(), terminalY - 23, (Button.OnPress) b -> {});
                icon.set(button, new ItemStack(tab.category() < 0 ? Items.GRASS_BLOCK
                    : tab.category() % 2 == 0 ? Items.COPPER_INGOT : Items.APPLE));
                chosen.setBoolean(button, tab.category() == selected);
                button.render(graphics, -1, -1, 0);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Tab widget no longer matches the renderer test", error);
        }
        if (navigation.paginated()) {
            var previous = Button.builder(Component.literal("<"), b -> {}).bounds(x + navigation.previousX(), terminalY - 20, 16, 18).build();
            var next = Button.builder(Component.literal(">"), b -> {}).bounds(x + navigation.nextX(), terminalY - 20, 16, 18).build();
            previous.active = page > 0;
            next.active = page + 1 < navigation.count();
            previous.render(graphics, -1, -1, 0);
            next.render(graphics, -1, -1, 0);
        }
    }
}
