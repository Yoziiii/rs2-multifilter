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
    @Test void selectedOpenBaseCoversOnlyItsOwnFourPixelsOfTheTerminalTopEdge() {
        var graphics = mock(GuiGraphics.class);
        RsUi.iconTab(graphics, 50, 7, true); // Terminal top is 30.
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 0, 52, 7, 26, 7);
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 7, 52, 14, 26, 16);
        verify(graphics).blitSprite(RsUi.TAB_SELECTED, 26, 32, 0, 28, 52, 30, 26, 4);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
        verifyNoMoreInteractions(graphics);
    }

    @Test void unselectedUsesItsNativeBorderAndStopsBeforeTheTerminalTopEdge() {
        var graphics = mock(GuiGraphics.class);
        RsUi.iconTab(graphics, 82, 7, false);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 0, 84, 7, 26, 7);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 7, 84, 14, 26, 12);
        verify(graphics).blitSprite(RsUi.TAB_UNSELECTED, 26, 32, 0, 28, 84, 26, 26, 4);
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
