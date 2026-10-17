package dev.rs2multifilter.minecraft.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CreativeTabRenderingTest {
    private java.awt.image.BufferedImage resource(String path) throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return ImageIO.read(stream);
        }
    }

    /** Rasterize the actual blit calls against the actual RS corners. No screenshot estimate. */
    private java.awt.image.BufferedImage joinedTab(int tabOffset, boolean selected) throws Exception {
        return joinedTab(tabOffset, selected, 26);
    }

    private java.awt.image.BufferedImage joinedTab(int tabOffset, boolean selected, int width) throws Exception {
        var canvas = new java.awt.image.BufferedImage(260, 80, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var paint = canvas.createGraphics();
        paint.drawImage(resource("assets/refinedstorage/textures/gui/grid.png").getSubimage(0, 0, 193, 36), 10, 30, null);
        paint.drawImage(resource("assets/refinedstorage/textures/gui/generic_filter.png").getSubimage(180, 0, 30, 36), 207, 30, null);
        var graphics = mock(GuiGraphics.class);
        doAnswer(call -> {
            ResourceLocation id = call.getArgument(0);
            var sprite = resource("assets/" + id.getNamespace() + "/textures/gui/sprites/" + id.getPath() + ".png");
            int u = call.getArgument(3), v = call.getArgument(4);
            int x = call.getArgument(5), y = call.getArgument(6);
            int w = call.getArgument(7), h = call.getArgument(8);
            paint.drawImage(sprite.getSubimage(u, v, w, h), x, y, null);
            return null;
        }).when(graphics).blitSprite(any(ResourceLocation.class), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        RsUi.iconTab(graphics, 10 + tabOffset, 7, selected, 10, width);
        paint.dispose();
        return canvas;
    }

    @Test void selectedAllKeepsTheNativeLeftVerticalEdgeInsteadOfFlaringOut() throws Exception {
        var joined = joinedTab(0, true);
        for (int y = 30; y < 36; y++) {
            assertEquals(0xFF000000, joined.getRGB(10, y));
            assertEquals(0xFFFFFFFF, joined.getRGB(11, y));
            assertEquals(0xFFFFFFFF, joined.getRGB(12, y));
            assertEquals(0, joined.getRGB(9, y));
        }
    }

    @Test void lastTabOuterEdgeMatchesTheTerminalInBothStatesWithoutPaintingIntoTheGap() throws Exception {
        var original = resource("assets/refinedstorage/textures/gui/grid.png");
        for (boolean selected : new boolean[]{false, true}) {
            var joined = joinedTab(168, selected, 25);
            // Tab edge and the straight terminal side share logical pixel 192.
            for (int y = 13; y < (selected ? 34 : 30); y++) {
                assertEquals(0xFF000000, joined.getRGB(202, y));
                assertEquals(0xFF555555, joined.getRGB(201, y));
                for (int x = 203; x < 207; x++) assertEquals(0, joined.getRGB(x, y));
            }
            for (int y = 0; y < 4; y++) {
                for (int x = 190; x < 193; x++) {
                    int expected = selected ? (x == 192 ? 0xFF000000 : 0xFF555555) : original.getRGB(x, y);
                    assertEquals(expected, joined.getRGB(10 + x, 30 + y));
                }
                for (int x = 193; x < 197; x++) assertEquals(0, joined.getRGB(10 + x, 30 + y));
            }
            assertEquals(selected ? 0xFFC6C6C6 : 0xFF8B8B8B, joined.getRGB(190, 20));
        }
    }

    @Test void rightCapUsesNativePixelsInsteadOfScalingTheThreePixelBevel() throws Exception {
        var sprite = resource("assets/minecraft/textures/gui/sprites/" + RsUi.TAB_SELECTED_RIGHT.getPath() + ".png");
        for (int y = 28; y < 32; y++) {
            assertEquals(0xFF555555, sprite.getRGB(23, y));
            assertEquals(0xFF555555, sprite.getRGB(24, y));
            assertEquals(0xFF000000, sprite.getRGB(25, y));
        }
        var g = mock(GuiGraphics.class);
        RsUi.iconTab(g, 218, 7, true, 50, 25);
        verify(g).blitSprite(RsUi.TAB_SELECTED_RIGHT, 26, 32, 23, 28, 240, 30, 3, 4);
        verify(g, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }

    @Test void eighthTabKeepsTheUpgradePanelsLeftCornerAndTheGapIntact() throws Exception {
        var original = resource("assets/refinedstorage/textures/gui/generic_filter.png");
        var joined = joinedTab(196, true);
        for (int y = 0; y < 4; y++) {
            for (int x = 193; x < 197; x++) assertEquals(0, joined.getRGB(10 + x, 30 + y));
            for (int x = 0; x < 4; x++)
                assertEquals(original.getRGB(180 + x, y), joined.getRGB(207 + x, 30 + y));
        }
        assertEquals(0xFFC6C6C6, joined.getRGB(215, 33));
    }

    @Test void selectedOpenBaseCoversOnlyItsOwnFourPixelsOfTheTerminalTopEdge() {
        var graphics = mock(GuiGraphics.class);
        RsUi.iconTab(graphics, 50, 7, true, 0); // Terminal top is 30.
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 0, 50, 7, 26, 7);
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 7, 50, 14, 26, 16);
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 28, 50, 30, 26, 4);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verifyNoMoreInteractions(graphics);
    }

    @Test void unselectedUsesItsNativeBorderAndStopsBeforeTheTerminalTopEdge() {
        var graphics = mock(GuiGraphics.class);
        RsUi.iconTab(graphics, 82, 7, false, 0);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 0, 82, 7, 26, 7);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 7, 82, 14, 26, 12);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 28, 82, 26, 26, 4);
        verifyNoMoreInteractions(graphics);
    }

    @Test void originalCreativeSpritesHaveAFlatInteriorAndAnOpenSelectedBase() throws Exception {
        for (var sprite : new ResourceLocation[]{RsUi.TAB_SELECTED, RsUi.TAB_UNSELECTED}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(
                    "assets/minecraft/textures/gui/sprites/" + sprite.getPath() + ".png")) {
                assertNotNull(stream, sprite.toString());
                var image = ImageIO.read(stream);
                assertEquals(26, image.getWidth());
                assertEquals(32, image.getHeight());
                int body = sprite.equals(RsUi.TAB_SELECTED) ? 0xFFC6C6C6 : 0xFF8B8B8B;
                for (int x = 4; x < 22; x++) for (int y = 7; y < 28; y++)
                    assertEquals(body, image.getRGB(x, y));
                if (sprite.equals(RsUi.TAB_SELECTED))
                    for (int x = 0; x < 26; x++) assertEquals(body, image.getRGB(x, 31));
                else assertEquals(0xFF000000, image.getRGB(25, 31));
            }
        }
    }

    @Test void nativePagingButtonResourcesUseTheVanillaNineSliceBorders() throws Exception {
        for (String name : new String[]{"button", "button_highlighted", "button_disabled"}) {
            try (var stream = getClass().getClassLoader().getResourceAsStream(
                    "assets/minecraft/textures/gui/sprites/widget/" + name + ".png.mcmeta")) {
                assertNotNull(stream, name);
                var metadata = com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
                assertEquals("nine_slice", metadata.get("type").getAsString());
                assertEquals(20, metadata.get("height").getAsInt());
                assertEquals(name.equals("button_disabled") ? 1 : 3, metadata.get("border").getAsInt());
            }
        }
    }
}
