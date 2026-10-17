package dev.rs2multifilter.smoke;

import dev.rs2multifilter.minecraft.client.CategoryNavigation;
import dev.rs2multifilter.minecraft.client.GridTabs;
import dev.rs2multifilter.minecraft.client.RsUi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;

/** CI-only render samples: actual tab widgets, not a simulated terminal session. */
final class CreativeTabPreview {
    private static final ArrayList<Map<String, Object>> measurements = new ArrayList<>();
    static final int CASES = 8;
    static void capture(GuiGraphics graphics, int scale, int scenario) {
        int[] counts = {1, 1, 6, 6, 8, 8, 13, 13};
        int[] pages = {0, 0, 0, 0, 0, 0, 1, 2};
        int[] selected = {-1, 0, -1, 5, -1, 5, 11, 12};
        var window = Minecraft.getInstance().getWindow();
        int x = (window.getGuiScaledWidth() - GridTabs.HEADER_WIDTH) / 2;
        int terminalY = (window.getGuiScaledHeight() - 82) / 2 + 12;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        draw(graphics, x, terminalY, counts[scenario], pages[scenario], selected[scenario]);
        // Test-only guides mark the measured main-left and upgrade-panel-right boundaries.
        graphics.fill(x - 1, terminalY - 29, x, terminalY + 83, 0xFF548DE1);
        graphics.fill(x + GridTabs.HEADER_WIDTH, terminalY - 29,
            x + GridTabs.HEADER_WIDTH + 1, terminalY + 83, 0xFF548DE1);
        graphics.pose().popPose();
        graphics.flush();
        try (var image = Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())) {
            String filename = "category-alignment-scale-" + scale + "-case-" + scenario + ".png";
            image.writeToFile(Path.of(filename));
            var sample = new LinkedHashMap<String, Object>();
            sample.put("file", filename);
            sample.put("requestedScale", scale);
            sample.put("actualScale", window.getGuiScale());
            sample.put("framebufferWidth", image.getWidth());
            sample.put("framebufferHeight", image.getHeight());
            sample.put("guiWidth", window.getGuiScaledWidth());
            sample.put("guiHeight", window.getGuiScaledHeight());
            sample.put("left", x);
            sample.put("top", terminalY);
            sample.put("upgradeRight", x + GridTabs.HEADER_WIDTH);
            sample.put("customCategories", counts[scenario]);
            sample.put("page", pages[scenario]);
            sample.put("selected", selected[scenario]);
            measurements.add(sample);
            Files.writeString(Path.of("category-alignment-measurements.json"),
                new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(measurements), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to capture tab render regression sample", error);
        }
        System.out.println("RS2MF_CREATIVE_TAB_RENDER_OK scale=" + window.getGuiScale() + " case=" + scenario);
    }

    private static void draw(GuiGraphics graphics, int x, int terminalY, int count, int page, int selected) {
        var names = IntStream.range(0, count).mapToObj(i -> "C" + i).toList();
        var navigation = new CategoryNavigation(names, String::length, GridTabs.HEADER_WIDTH);
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, count + " categories / page " + (page + 1), x, terminalY - 36, 0xFFFFFF, false);
        // Actual RS grid top corners, not a generic nine-sliced approximation.
        graphics.blit(RsUi.id("textures/gui/grid.png"), x, terminalY, 0, 0, 193, 36);
        graphics.blit(RsUi.FILTER, x + 197, terminalY, 180, 0, 30, 82);
        graphics.drawString(font, "RS panel edge", x + 6, terminalY + 9, 0x404648, false);
        try {
            var type = Class.forName("dev.rs2multifilter.minecraft.client.GridTabs$TabButton");
            var constructor = type.getDeclaredConstructor(int.class, int.class, Button.OnPress.class);
            constructor.setAccessible(true);
            var icon = type.getDeclaredField("icon");
            var chosen = type.getDeclaredField("chosen");
            var terminalLeft = type.getDeclaredField("terminalLeft");
            terminalLeft.setAccessible(true);
            icon.setAccessible(true);
            chosen.setAccessible(true);
            var shown = navigation.tabs(page);
            for (var tab : shown) {
                var button = (Button) constructor.newInstance(x + tab.x(), terminalY - 23, (Button.OnPress) b -> {});
                button.setWidth(GridTabs.terminalTabWidth(tab, tab == shown.getLast()));
                terminalLeft.setInt(button, x);
                icon.set(button, new ItemStack(tab.category() < 0 ? Items.GRASS_BLOCK
                    : tab.category() % 2 == 0 ? Items.COPPER_INGOT : Items.APPLE));
                chosen.setBoolean(button, tab.category() == selected);
                button.render(graphics, -1, -1, 0);
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Tab widget no longer matches the renderer test", error);
        }
        if (navigation.paginated()) {
            var previous = Button.builder(Component.literal("<"), b -> {}).bounds(x + navigation.previousX(), terminalY - 20, CategoryNavigation.ARROW_WIDTH, 18).build();
            var next = Button.builder(Component.literal(">"), b -> {}).bounds(x + navigation.nextX(), terminalY - 20, CategoryNavigation.ARROW_WIDTH, 18).build();
            previous.active = page > 0;
            next.active = page + 1 < navigation.count();
            previous.render(graphics, -1, -1, 0);
            next.render(graphics, -1, -1, 0);
        }
    }
}
